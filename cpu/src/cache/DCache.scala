package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class DCache(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val cpuIO   = new CpuIO
    val memIO   = new AXI_BUNDLE
    val clintIO = Flipped(new cpu.component.ClintIO)
    val plicIO  = Flipped(new cpu.component.SimplePlicIO)
    val wb      = Flipped(Irrevocable(Bool()))
  })

  private val rand = MaximalPeriodGaloisLFSR(2)

  private val idle::starting::compare::writeback::allocate::answering::passing::backall::clint::plic::Nil = Enum(10)
  private val state = RegInit(UInt(4.W), idle)
  private val received = RegInit(0.U(LogBurstLen.W))
  private val backAllInnerState = RegInit(0.U(1.W))
  private val writingBackAll = RegInit(0.B)

  private val ARVALID = RegInit(0.B)

  private val addr       = RegInit(0.U(alen.W))
  private val reqData    = Reg(UInt(xlen.W))
  private val reqRw      = RegInit(0.B)
  private val reqSize    = RegInit(0.U(3.W))
  private val reqWMask   = RegInit(0.U((xlen / 8).W))
  private val addrOffset = addr(Offset - 1, log2Ceil(xlen / 8))
  private val addrIndex  = addr(Index + Offset - 1, Offset)
  private val addrTag    = addr(alen - 1, Index + Offset)
  private val memAddr    = addr(alen - 1, Offset) ## 0.U(Offset.W)
  private val realIndex  = Mux(state === idle && isZmb.B, io.cpuIO.cpuReq.addr(Index + Offset - 1, Offset), addrIndex)

  io.memIO.ar.bits.id     := 1.U // 1 for MEM
  io.memIO.ar.bits.len    := (BurstLen - 1).U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.memIO.ar.bits.size   := axSize.U // 2^(ARSIZE) bytes per AXI Transfer
  io.memIO.ar.bits.burst  := 1.U // 1 for INCR type
  io.memIO.ar.bits.lock   := 0.U // since we do not use it yet
  io.memIO.ar.bits.cache  := 0.U // since we do not use it yet
  io.memIO.ar.bits.prot   := 0.U // since we do not use it yet
  io.memIO.ar.bits.qos    := DontCare
  io.memIO.ar.bits.user   := DontCare
  io.memIO.ar.bits.region := DontCare
  io.memIO.ar.bits.addr   := memAddr
  io.memIO.ar.valid       := ARVALID

  io.memIO.r.ready := 1.B

  private val rbytes = WireDefault(VecInit((0 until Buslen / 8).map { i => io.memIO.r.bits.data(i * 8 + 7, i * 8) }))

  private val ramValid = SyncReadRegs(0.B        , IndexSize, Associativity)
  private val ramDirty = SyncReadRegs(0.B        , IndexSize, Associativity)
  private val ramTag   = SyncReadRegs(UInt(Tag.W), IndexSize, Associativity)
  private val ramData  = SinglePortRam(clock, BlockSize * 8, IndexSize, Associativity)

  private val hit = WireDefault(0.B)
  private val grp = Reg(UInt(log2Ceil(Associativity).W))
  private val wen = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  private val bwe = WireDefault(VecInit(Seq.fill(BlockSize)(0.B)))

  private val preValid = ramValid.preRead(addrIndex, 1.B)
  private val preDirty = ramDirty.preRead(addrIndex, 1.B)
  private val preTag   = ramTag  .preRead(addrIndex, 1.B)
  private val data     = ramData .read   (realIndex, 1.B)

  private val way = Reg(UInt(log2Ceil(Associativity).W))

  private val isPeripheral = IsPeripheral(io.cpuIO.cpuReq.addr)
  private val isClint      = if (useClint) IsClint(io.cpuIO.cpuReq.addr) else null
  if (useClint) {
    io.clintIO.addr  := isClint.address
    io.clintIO.wdata := reqData
    io.clintIO.wen   := state === clint && reqRw
  } else io.clintIO <> DontCare
  private val isPlic = if (usePlic) IsPlic(io.cpuIO.cpuReq.addr) else null
  if (usePlic) {
    io.plicIO.addr  := isPlic.address
    io.plicIO.wdata := Mux(isPlic.address(2), reqData(63, 32), reqData(31, 0))
    io.plicIO.wen   := state === plic && reqRw
  } else io.plicIO <> DontCare

  private val wbBuffer    = WbBuffer(io.memIO, data(way), preTag(way) ## addrIndex ## 0.U(Offset.W))
  private val passThrough = PassThrough(false)(io.memIO, wbBuffer.ready, addr, reqData, reqWMask, reqRw, reqSize)

  private val inBuffer = Reg(UInt((BlockSize * 8).W))

  private val dwordData = Mux1H(if (isZmb) Seq.tabulate(Associativity)(x =>
    (grp === x.U) -> RegNext(VecInit((0 until BlockSize / 8).map { i =>
      data(x)(i * 64 + 63, i * 64)
  })(addrOffset))) else Seq.tabulate(Associativity)(x =>
    (grp === x.U) -> VecInit((0 until BlockSize / 8).map { i =>
      data(x)(i * 64 + 63, i * 64)
  })(addrOffset)))

  private val wdata  = WireDefault(UInt((8 * BlockSize).W), inBuffer.asUInt)
  private val wvalid = 1.B
  private val wdirty = WireDefault(0.B)
  private val wtag   = addrTag

  ramValid.write(addrIndex, wvalid, wen)
  ramDirty.write(addrIndex, wdirty, wen)
  ramTag  .write(addrIndex, wtag  , wen)
  ramData .write(addrIndex, wdata , wen, bwe.asUInt)

  io.cpuIO.cpuResult.ready := hit
  io.cpuIO.cpuResult.data  := dwordData

  private val useEmpty = RegInit(0.B)
  private val readBack = WireDefault(0.B)
  private val willDrop = RegInit(0.B)

  private val compareHit = RegInit(0.B)
  private val compDirty  = RegInit(VecInit(Seq.fill(Associativity)(0.B)))
  private val wbBufferGo = RegInit(0.B)
  private val answerData = Reg(UInt(xlen.W))

  private val plicReadHit = RegInit(0.B)
  private val plicRdata   = RegNext(io.plicIO.rdata)

  when(io.cpuIO.cpuReq.valid && state =/= writeback && state =/= allocate && state =/= backall) {
    addr     := io.cpuIO.cpuReq.addr
    reqData  := io.cpuIO.cpuReq.data
    reqRw    := io.cpuIO.cpuReq.rw
    reqSize  := io.cpuIO.cpuReq.size
    reqWMask := io.cpuIO.cpuReq.wmask
  }

  io.wb.ready := 0.B
  when(state === idle) {
    willDrop := 0.B
    when(io.cpuIO.cpuReq.valid) {
      state := starting
      when(isPeripheral) { state := passing }
      if (useClint) when(isClint) { state := clint }
      if (usePlic)  when(isPlic)  { state := plic  }
    }.elsewhen(io.wb.valid) { state := backall; addr := 0.U; way := 0.U; writingBackAll := 1.B }
  }
  when(state === starting) {
    state := compare
    grp := Mux1H(Seq.tabulate(Associativity)(i => (preValid(i) && preTag(i) === addrTag) -> i.U))
    compareHit := VecInit(Seq.tabulate(Associativity)(i => preValid(i) && preTag(i) === addrTag)).reduceTree(_ | _)
    way := MuxLookup(0.B, rand, preValid zip Seq.tabulate(Associativity)(_.U))
    compDirty := preDirty
    useEmpty := !preValid.asUInt.andR
    wbBufferGo := wbBuffer.used && (io.memIO.ar.bits.addr === wbBuffer.wbAddr)
  }
  when(state === compare) {
    hit := compareHit
    when(compareHit) {
      state := idle
      when(reqRw === 1.U) {
        wen(grp) := 1.B
        wdirty := 1.B
        wdata := Fill(BlockSize / 8, reqData)
        for (i <- 0 until xlen / 8)
          bwe(addrOffset ## i.U(log2Ceil(xlen / 8).W)) := reqWMask(i)
      }
    }.elsewhen(useEmpty || !compDirty(way)) { // have empty cache line or the selected cacheline is clean
      when(wbBufferGo) { readBack := 1.B; grp := way; if (isZmb) state := starting else compareHit := 1.B }
      .otherwise { ARVALID := 1.B; state := allocate }
    }.elsewhen(wbBufferGo) { // swap wbBuffer and cache line
      when(wbBuffer.ready) { readBack := 1.B; wbBuffer.valid := 1.B; grp := way; if (isZmb) state := starting else compareHit := 1.B }
    }.otherwise { state := writeback }
  }
  when(state === writeback) {
    wbBuffer.valid := 1.B
    when(wbBuffer.ready && wbBuffer.valid) {
      ARVALID := !writingBackAll
      state := Mux(writingBackAll, backall, allocate)
      way   := Mux(writingBackAll, Mux(way === (Associativity - 1).U, 0.U, way + 1.U), way)
      addr  := Mux(writingBackAll && (way === (Associativity - 1).U), addrTag ## (addrIndex + 1.U) ## addrOffset ## 0.U(log2Ceil(xlen / 8).W), addr)
    }
  }
  when(state === allocate) {
    when(io.memIO.r.fire) {
      inBuffer := rbytes.asUInt ## inBuffer(inBuffer.getWidth - 1, rbytes.asUInt.getWidth)
      when(received === (BurstLen - 1).U) {
        received := 0.U
        state    := answering
      }.otherwise { received := received + 1.U }
    }
    when(io.memIO.ar.fire) { ARVALID := 0.B }
    when(received === addrOffset) {
      when(reqRw) { (0 until Buslen / 8).foreach(i => when(reqWMask(i)) { rbytes(i.U(axSize.W)) := reqData(i * 8 + 7, i * 8) }) }
      .otherwise { answerData := io.memIO.r.bits.data }
    }
  }
  when(state === answering) {
    wen(way) := 1.B
    bwe.foreach(_ := 1.B)
    wdirty := reqRw
    hit := ~willDrop
    io.cpuIO.cpuResult.data := answerData
    state := idle
  }
  when(state === passing) {
    hit := passThrough.finish
    passThrough.valid := !passThrough.finish
    io.cpuIO.cpuResult.data := passThrough.rdata
    when(hit) { state := idle }
    when(willDrop) { io.cpuIO.cpuResult.ready := 0.B; willDrop := 0.B }
  }
  if (!isZmb) when(state === backall) {
    val running::ending::Nil = Enum(2)
    when(backAllInnerState === running) {
      when(preValid(way) && preDirty(way)) { state := writeback }
      .otherwise {
        way  := Mux(way === (Associativity - 1).U, 0.U, way + 1.U)
        addr := Mux(way === (Associativity - 1).U, addrTag ## (addrIndex + 1.U) ## addrOffset ## 0.U(log2Ceil(xlen / 8).W), addr)
      }
      when(way === (Associativity - 1).U && addrIndex === (IndexSize - 1).U) { backAllInnerState := ending }
    }
    when(backAllInnerState === ending && wbBuffer.ready) {
      state             := idle
      backAllInnerState := running
      io.wb.ready       := 1.B
      writingBackAll    := 0.B
      ramDirty.reset
    }
  }
  if (useClint) when(state === clint) {
    hit   := 1.B
    state := idle
    io.cpuIO.cpuResult.data := io.clintIO.rdata
  }
  if (usePlic) when(state === plic) {
    when(hit) { state := idle }
    hit := reqRw || plicReadHit
    plicReadHit := ~hit
    io.cpuIO.cpuResult.data := Fill(Buslen / 32, plicRdata)
  }

  when(readBack) {
    wen(way) := 1.B
    bwe.foreach(_ := 1.B)
    wdata := wbBuffer.buffer
  }
  when(io.cpuIO.cpuReq.revoke) {
    when(state <= compare) { ARVALID := 0.B; state := idle }
    .otherwise { willDrop := 1.B }
  }
}

object DCache {
  def apply()(implicit p: Parameters): DCache = Module(new DCache)
}

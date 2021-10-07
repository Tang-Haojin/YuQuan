package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import utils.Convert._
import cpu.tools._

class DCache(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val cpuIO   = new CpuIO
    val memIO   = new AXI_BUNDLE
    val clintIO = Flipped(new cpu.component.ClintIO)
    val wb      = Flipped(Irrevocable(Bool()))
  })

  private val rand = MaximalPeriodGaloisLFSR(2)

  private val idle::compare::writeback::allocate::passing::backall::clint::Nil = Enum(7)
  private val state = RegInit(UInt(3.W), idle)
  private val received = RegInit(0.U(LogBurstLen.W))
  private val backAllInnerState = RegInit(0.U(1.W))
  private val writingBackAll = RegInit(0.B)

  private val ARVALID = RegInit(0.B)
  private val AWVALID = RegInit(0.B)
  private val RREADY  = RegInit(0.B)

  private val addr       = RegInit(0.U(alen.W))
  private val reqData    = RegInit(0.U(xlen.W))
  private val reqRw      = RegInit(0.B)
  private val reqSize    = RegInit(0.U(3.W))
  private val reqWMask   = RegInit(0.U((xlen / 8).W))
  private val addrOffset = addr(Offset - 1, 3)
  private val addrIndex  = WireDefault(UInt(Index.W), addr(Index + Offset - 1, Offset))
  private val addrTag    = addr(alen - 1, Index + Offset)
  private val memAddr    = addr(alen - 1, Offset) ## 0.U(Offset.W)

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

  io.memIO.r.ready := RREADY

  private val ramValid = SyncReadRegs(1, IndexSize, Associativity)
  private val ramDirty = SyncReadRegs(1, IndexSize, Associativity)
  private val ramTag   = SyncReadRegs(Tag, IndexSize, Associativity)
  private val ramData  = if (noCache) NoCacheRam(Associativity) else SinglePortRam(clock, BlockSize * 8, IndexSize, Associativity)

  private val hit = WireDefault(0.B)
  private val grp = WireDefault(0.U(log2Ceil(Associativity).W))
  private val wen = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  private val ren = { var x = 1.B; for (i <- wen.indices) { x = x | ~wen(i) }; x }

  private val valid = ramValid.read(addrIndex, 1.B)
  private val dirty = ramDirty.read(addrIndex, 1.B)
  private val tag   = ramTag  .read(addrIndex, 1.B)
  private val data  = ramData .read(addrIndex, 1.B)

  private val way = RegInit(0.U(log2Ceil(Associativity).W))
  private val wireWay = WireDefault(UInt(log2Ceil(Associativity).W), way)

  private val isPeripheral = IsPeripheral(io.cpuIO.cpuReq.addr)
  private val isClint      = IsClint     (io.cpuIO.cpuReq.addr)
  io.clintIO.addr  := isClint.address
  io.clintIO.wdata := reqData
  io.clintIO.wen   := state === clint && reqRw

  private val wbBuffer    = WbBuffer(io.memIO, data(wireWay), tag(way) ## addrIndex ## 0.U(Offset.W))
  private val passThrough = PassThrough(false)(io.memIO, wbBuffer.ready, addr, reqData, reqWMask, reqRw, reqSize)

  private val inBuffer = RegInit(VecInit(Seq.fill(BurstLen - 1)(0.U(xlen.W))))

  private val dwordData = WireDefault(VecInit((0 until BlockSize / 8).map { i =>
    if (noCache) 0.U else data(grp)(i * 64 + 63, i * 64)
  }))

  private val byteData = WireDefault(VecInit((0 until BlockSize).map { i =>
    if (noCache) 0.U else data(grp)(i * 8 + 7, i * 8)
  })); private val byteDatas = byteData.asUInt()

  private val wdata     = io.memIO.r.bits.data ## inBuffer.asUInt
  private val vecWvalid = VecInit(Seq.fill(Associativity)(1.U))
  private val vecWdirty = VecInit(Seq.fill(Associativity)(0.U))
  private val vecWtag   = VecInit(Seq.fill(Associativity)(addrTag))
  private val vecWdata  = WireDefault(VecInit(Seq.fill(Associativity)(wdata)))

  ramValid.write(addrIndex, vecWvalid, wen)
  ramDirty.write(addrIndex, vecWdirty, wen)
  ramTag  .write(addrIndex, vecWtag  , wen)
  ramData .write(addrIndex, vecWdata , wen)

  io.cpuIO.cpuResult.ready := hit
  io.cpuIO.cpuResult.data  := dwordData(addrOffset)

  private val useEmpty = WireDefault(0.B)
  private val readBack = WireDefault(0.B)

  io.wb.ready := 0.B
  when(state === idle) {
    when(io.cpuIO.cpuReq.valid) {
      state     := (if (noCache) passing else compare)
      addr      := io.cpuIO.cpuReq.addr
      reqData   := io.cpuIO.cpuReq.data
      reqRw     := io.cpuIO.cpuReq.rw
      reqSize   := io.cpuIO.cpuReq.size
      reqWMask  := io.cpuIO.cpuReq.wmask
      addrIndex := io.cpuIO.cpuReq.addr(Index + Offset - 1, Offset)
      way       := rand
      when(isPeripheral) { state := passing }
      when(isClint)      { state := clint   }
    }
    .elsewhen(io.wb.valid) { if (!noCache) { state := backall; addr := 0.U; way := 0.U; writingBackAll := 1.B } else io.wb.ready := 1.B }
  }
  if (!noCache) when(state === compare) {
    hit := VecInit(Seq.tabulate(Associativity)(i => valid(i) && tag(i) === addrTag)).asUInt.orR
    grp := Mux1H(Seq.tabulate(Associativity)(i => (valid(i) && tag(i) === addrTag) -> i.U))
    when(!hit) {
      useEmpty := !valid.asUInt.andR
      wireWay  := MuxLookup(0.B, way, valid zip Seq.tabulate(Associativity)(_.U))
    }
    way := wireWay
    when(hit) {
      state := idle
      when(reqRw === 1.U) {
        wen(grp)       := 1.B
        vecWdirty(grp) := 1.B
        vecWdata(grp)  := byteDatas
        for (i <- 0 until xlen / 8)
          when(reqWMask(i)) { byteData(addrOffset ## i.U(3.W)) := reqData(i * 8 + 7, i * 8) }
      }
    }.elsewhen(useEmpty || (!useEmpty && !dirty(wireWay))) { // have empty or clean cache line
      when(wbBuffer.used && (io.memIO.ar.bits.addr === wbBuffer.wbAddr)) {
        readBack := 1.B
        state    := idle
      }.otherwise { ARVALID := 1.B; state := allocate }
    }.elsewhen(wbBuffer.used && (io.memIO.ar.bits.addr === wbBuffer.wbAddr)) {
      when(wbBuffer.ready) { readBack := 1.B; wbBuffer.valid := 1.B; state := idle } // swap wbBuffer and cache line
    }.otherwise { state := writeback }
  }
  if (!noCache) when(state === writeback) {
    wbBuffer.valid := 1.B
    when(wbBuffer.ready && wbBuffer.valid) {
      ARVALID := !writingBackAll
      state := Mux(writingBackAll, backall, allocate)
      way   := Mux(writingBackAll, Mux(way === (Associativity - 1).U, 0.U, way + 1.U), way)
      addr  := Mux(writingBackAll && (way === (Associativity - 1).U), addrTag ## (addrIndex + 1.U) ## addrOffset ## 0.U(3.W), addr)
    }
  }
  if (!noCache) when(state === allocate) {
    when(io.memIO.r.fire()) {
      when(received === (BurstLen - 1).U) {
        received := 0.U
        state    := idle
        wen(way) := 1.B
        RREADY   := 0.B
      }.otherwise {
        received           := received + 1.U
        inBuffer(received) := io.memIO.r.bits.data
      }
    }.elsewhen(io.memIO.ar.fire()) {
      ARVALID := 0.B
      RREADY  := 1.B
    }
  }
  when(state === passing) {
    hit := passThrough.finish
    passThrough.valid := 1.B
    io.cpuIO.cpuResult.data := passThrough.rdata
    when(hit) { state := idle }
  }
  if (!noCache) when(state === backall) {
    val running::ending::Nil = Enum(2)
    when(backAllInnerState === running) {
      when(ramValid.preRead(way) && ramDirty.preRead(way)) { state := writeback }
      .otherwise {
        way  := Mux(way === (Associativity - 1).U, 0.U, way + 1.U)
        addr := Mux(way === (Associativity - 1).U, addrTag ## (addrIndex + 1.U) ## addrOffset ## 0.U(3.W), addr)
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
  when(state === clint) {
    hit   := 1.B
    state := idle
    io.cpuIO.cpuResult.data := io.clintIO.rdata
  }

  when(readBack) {
    wen(wireWay) := 1.B
    vecWdata(wireWay) := wbBuffer.buffer
  }
}

object DCache {
  def apply()(implicit p: Parameters): DCache = Module(new DCache)
}

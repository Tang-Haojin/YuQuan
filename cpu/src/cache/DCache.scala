package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import utils.Convert._
import cpu.tools._

class DCache(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val cpuIO = new CpuIO
    val memIO = new AXI_BUNDLE
  })

  val rand = MaximalPeriodGaloisLFSR(2)

  val idle::compare::writeback::allocate::passing::Nil = Enum(5)
  val state = RegInit(UInt(3.W), idle)
  val received = RegInit(0.U(LogBurstLen.W))

  val ARVALID = RegInit(0.B)
  val AWVALID = RegInit(0.B)
  val RREADY  = RegInit(0.B)

  val addr       = RegInit(0.U(alen.W))
  val reqData    = RegInit(0.U(xlen.W))
  val reqRw      = RegInit(0.B)
  val reqWMask   = RegInit(0.U((xlen / 8).W))
  val addrOffset = addr(Offset - 1, 3)
  val addrIndex  = WireDefault(UInt(Index.W), addr(Index + Offset - 1, Offset))
  val addrTag    = addr(alen - 1, Index + Offset)
  val memAddr    = addr(alen - 1, Offset) ## 0.U(Offset.W)

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

  val ramValid = SyncReadRegs(1, IndexSize, Associativity)
  val ramDirty = SyncReadRegs(1, IndexSize, Associativity)
  val ramTag   = SyncReadRegs(Tag, IndexSize, Associativity)
  val ramData  = if (noCache) NoCacheRam(Associativity) else SinglePortRam(clock, BlockSize * 8, IndexSize, Associativity)

  val hit = WireDefault(0.B)
  val grp = WireDefault(0.U(log2Ceil(Associativity).W))
  val wen = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  val ren = { var x = 1.B; for (i <- wen.indices) { x = x | ~wen(i) }; x }

  val valid = ramValid.read(addrIndex, 1.B)
  val dirty = ramDirty.read(addrIndex, 1.B)
  val tag   = ramTag  .read(addrIndex, 1.B)
  val data  = ramData .read(addrIndex, 1.B)

  val way = RegInit(0.U(log2Ceil(Associativity).W))
  val wireWay = WireDefault(UInt(log2Ceil(Associativity).W), way)

  val isPeripheral = IsPeripheral(io.cpuIO.cpuReq.addr)
  private val passThroughAxsize = RegInit(log2Ceil(32 / 8).U(3.W))

  val wbBuffer    = WbBuffer(io.memIO, data(wireWay), tag(way) ## addrIndex ## 0.U(Offset.W))
  val passThrough = PassThrough(false)(io.memIO, wbBuffer.ready, addr, reqData, reqWMask, reqRw, passThroughAxsize)

  val inBuffer = RegInit(VecInit(Seq.fill(BurstLen - 1)(0.U(xlen.W))))

  val dwordData = WireDefault(VecInit((0 until BlockSize / 8).map { i =>
    if (noCache) 0.U else data(grp)(i * 64 + 63, i * 64)
  }))

  val byteData = WireDefault(VecInit((0 until BlockSize).map { i =>
    if (noCache) 0.U else data(grp)(i * 8 + 7, i * 8)
  })); val byteDatas = byteData.asUInt()

  val wdata     = io.memIO.r.bits.data ## inBuffer.asUInt
  val vecWvalid = VecInit(Seq.fill(Associativity)(1.U))
  val vecWdirty = VecInit(Seq.fill(Associativity)(0.U))
  val vecWtag   = VecInit(Seq.fill(Associativity)(addrTag))
  val vecWdata  = WireDefault(VecInit(Seq.fill(Associativity)(wdata)))

  ramValid.write(addrIndex, vecWvalid, wen)
  ramDirty.write(addrIndex, vecWdirty, wen)
  ramTag  .write(addrIndex, vecWtag  , wen)
  ramData .write(addrIndex, vecWdata , wen)

  io.cpuIO.cpuResult.ready := hit
  io.cpuIO.cpuResult.data  := dwordData(addrOffset)

  val useEmpty = WireDefault(0.B)
  val readBack = WireDefault(0.B)

  when(state === idle) {
    when(io.cpuIO.cpuReq.valid) {
      state     := (if (noCache) passing else compare)
      addr      := io.cpuIO.cpuReq.addr
      reqData   := io.cpuIO.cpuReq.data
      reqRw     := io.cpuIO.cpuReq.rw
      reqWMask  := io.cpuIO.cpuReq.wmask
      addrIndex := io.cpuIO.cpuReq.addr(Index + Offset - 1, Offset)
      way       := rand
      when(isPeripheral) { state := passing; passThroughAxsize := log2Ceil(32 / 8).U }
      if (noCache) when(!isPeripheral) { passThroughAxsize := log2Ceil(64 / 8).U }
    }
  }
  if (!noCache) when(state === compare) {
    for (i <- 0 until Associativity)
      when(valid(i.U)) { when(tag(i.U) === addrTag) { hit      := 1.B; grp     := i.U } }
      .otherwise       { way := i.U; when(!hit)     { useEmpty := 1.B; wireWay := i.U } }
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
    when(wbBuffer.ready && wbBuffer.valid) { ARVALID := 1.B; state := allocate }
  }
  if (!noCache) when(state === allocate) {
    when(io.memIO.r.fire) {
      when(received === (BurstLen - 1).U) {
        received := 0.U
        state    := idle
        wen(way) := 1.B
        RREADY   := 0.B
      }.otherwise {
        received           := received + 1.U
        inBuffer(received) := io.memIO.r.bits.data
      }
    }.elsewhen(io.memIO.ar.fire) {
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

  when(readBack) {
    wen(wireWay) := 1.B
    vecWdata(wireWay) := wbBuffer.buffer
  }
}

object DCache {
  def apply(implicit p: Parameters): DCache = Module(new DCache)
}

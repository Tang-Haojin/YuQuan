package cpu.cache

import chisel3._
import chisel3.util._

import cpu.config.GeneralConfig._
import cpu.config.CacheConfig.ICache._
import tools._
import chisel3.util.random._

class ICache extends Module {
  val io = IO(new Bundle {
    val cpuIO = new CpuIO
    val memIO = new AxiMasterReadChannel
  })

  val rand = GaloisLFSR.maxPeriod(2)
  val idle::compare::allocate::Nil = Enum(3)
  val state = RegInit(UInt(2.W), idle)
  val received = RegInit(0.U(LogBurstLen.W))

  val addr       = RegInit(0.U(XLEN.W))
  val addrOffset = addr(Offset - 1, 0)
  val addrIndex  = WireDefault(UInt(Index.W), addr(Index + Offset - 1, Offset))
  val addrTag    = addr(XLEN - 1, Index + Offset)
  val memAddr    = addr(XLEN - 1, Offset) ## 0.U(Offset.W)

  val ARVALID = RegInit(0.B); val RREADY  = RegInit(0.B)
  ICacheMemIODefault(io.memIO, ARVALID, memAddr, RREADY)

  val ramValid = SyncReadMem(IndexSize, Vec(Associativity, Bool()))
  val ramTag   = SyncReadMem(IndexSize, Vec(Associativity, UInt(Tag.W)))
  val ramData  = SyncReadMem(IndexSize, Vec(Associativity, UInt((BlockSize * 8).W)))

  val hit = WireDefault(0.B)
  val grp = WireDefault(0.U(log2Ceil(Associativity).W))

  val valid = ramValid.read(addrIndex, 1.B)
  val tag   = ramTag  .read(addrIndex, 1.B)
  val data  = ramData .read(addrIndex, 1.B)

  val way = RegInit(0.U(log2Ceil(Associativity).W))
  val writeBuffer = RegInit(VecInit(Seq.fill(BurstLen - 1)(0.U(XLEN.W))))
  val wordData = WireDefault(VecInit((0 until BlockSize / 4).map { i =>
    data(grp)(i * 32 + 31, i * 32)
  }))

  val wen       = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  val wdata     = io.memIO.axiRd.RDATA ## writeBuffer.asUInt
  val vecWvalid = VecInit(Seq.fill(Associativity)(1.B))
  val vecWtag   = VecInit(Seq.fill(Associativity)(addrTag))
  val vecWdata  = VecInit(Seq.fill(Associativity)(wdata))

  ramValid.write(addrIndex, vecWvalid, wen)
  ramTag  .write(addrIndex, vecWtag  , wen)
  ramData .write(addrIndex, vecWdata , wen)

  io.cpuIO.cpuResult.ready := hit
  io.cpuIO.cpuResult.data  := wordData(addrOffset(Offset - 1, 2))

  when(io.cpuIO.cpuReq.valid) {
    addr      := io.cpuIO.cpuReq.addr
    addrIndex := io.cpuIO.cpuReq.addr(Index + Offset - 1, Offset)
  }

  when(state === idle && io.cpuIO.cpuReq.valid) { state := compare }
  when(state === compare) {
    ARVALID := 1.B
    state   := allocate
    way     := rand
    when(hit) {
      state := idle
      ARVALID := 0.B
      when(io.cpuIO.cpuReq.valid) { state := compare }
    }
    for (i <- 0 until Associativity)
      when(valid(i.U)) { when(tag(i.U) === addrTag) { hit := 1.B; grp := i.U } }
      .otherwise       { way := i.U }
  }
  when(state === allocate) {
    when(io.memIO.axiRd.RREADY && io.memIO.axiRd.RVALID) {
      when(received === (BurstLen - 1).U) {
        received := 0.U
        state    := compare
        wen(way) := 1.B
        RREADY   := 0.B
      }.otherwise {
        received              := received + 1.U
        writeBuffer(received) := io.memIO.axiRd.RDATA
      }
    }.elsewhen(io.memIO.axiRa.ARREADY && io.memIO.axiRa.ARVALID) {
      ARVALID := 0.B
      RREADY  := 1.B
    }
  }
}

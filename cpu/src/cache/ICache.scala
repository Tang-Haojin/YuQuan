package cpu.cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class ICache(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val cpuIO = new CpuIO
    val memIO = new AxiMasterChannel
  })

  val rand = GaloisLFSR.maxPeriod(2)
  val idle::compare::allocate::passing::Nil = Enum(4)
  val state = RegInit(UInt(2.W), idle)
  val received = RegInit(0.U(LogBurstLen.W))

  val addr       = RegInit(0.U(alen.W))
  val addrOffset = addr(Offset - 1, 0)
  val addrIndex  = WireDefault(UInt(Index.W), addr(Index + Offset - 1, Offset))
  val addrTag    = addr(alen - 1, Index + Offset)
  val memAddr    = addr(alen - 1, Offset) ## 0.U(Offset.W)

  val ARVALID = RegInit(0.B); val RREADY  = RegInit(0.B)
  ICacheMemIODefault(io.memIO, ARVALID, memAddr, RREADY)

  val ramValid = SyncReadMem(IndexSize, Vec(Associativity, Bool()))
  val ramTag   = SyncReadMem(IndexSize, Vec(Associativity, UInt(Tag.W)))
  val ramData  = SinglePortRam(clock, BlockSize * 8, IndexSize, Associativity)

  val hit = WireDefault(0.B)
  val grp = WireDefault(0.U(log2Ceil(Associativity).W))
  val wen = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  val ren = { var x = 1.B; for (i <- wen.indices) { x = x | ~wen(i) }; x }

  val valid = ramValid.read(addrIndex, ren)
  val tag   = ramTag  .read(addrIndex, ren)
  val data  = ramData .read(addrIndex, ren)

  val way = RegInit(0.U(log2Ceil(Associativity).W))
  val writeBuffer = RegInit(VecInit(Seq.fill(BurstLen - 1)(0.U(xlen.W))))
  val wordData = WireDefault(VecInit((0 until BlockSize / 4).map { i =>
    data(grp)(i * 32 + 31, i * 32)
  }))

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

  val isPeripheral = IsPeripheral(io.cpuIO.cpuReq.addr)
  val passThrough  = PassThrough(true)(io.memIO, 0.B, addr, 0.U, 0.U, 0.B)(p.alterPartial({
    case AxSIZE => log2Ceil(32 / 8)
  }))

  when(state === idle && io.cpuIO.cpuReq.valid) {
    state := compare
    when(isPeripheral) { state := passing }
  }
  when(state === compare) {
    ARVALID := 1.B
    state   := allocate
    way     := rand
    when(hit) {
      state := idle
      ARVALID := 0.B
      when(io.cpuIO.cpuReq.valid) {
        state := compare
        when(isPeripheral) { state := passing }
      }
    }
    for (i <- 0 until Associativity)
      when(valid(i.U)) { when(tag(i.U) === addrTag) { hit := 1.B; grp := i.U } }
      .otherwise       { way := i.U }
  }
  when(state === allocate) {
    when(io.memIO.axiRd.RREADY && io.memIO.axiRd.RVALID) {
      when(received === (BurstLen - 1).U) {
        received := 0.U
        state    := idle
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
  when(state === passing) {
    hit := passThrough.finish
    passThrough.valid := 1.B
    io.cpuIO.cpuResult.data := passThrough.rdata
    when(addr(2)) {
      io.cpuIO.cpuResult.data := passThrough.rdata(63, 32)
    }
    when(hit) {
      state := idle
      when(io.cpuIO.cpuReq.valid) {
        state := compare
        when(isPeripheral) { state := passing }
      }
    }
  }
}

object ICache {
  def apply(implicit p: Parameters): ICache = {
    val t = Module(new ICache)
    t.io.memIO.axiWa := DontCare
    t.io.memIO.axiWd := DontCare
    t.io.memIO.axiWr := DontCare
    t
  }
}

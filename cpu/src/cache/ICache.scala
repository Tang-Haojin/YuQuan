package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import utils.Convert._
import cpu.tools._

class ICache(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val cpuIO  = new CpuIO(32)
    val memIO  = new AXI_BUNDLE
    val inv    = Flipped(Irrevocable(Bool()))
    val jmpBch = Input (Bool())
  })

  private val rand = MaximalPeriodGaloisLFSR(2)
  private val idle::starting::compare::allocate::passing::Nil = Enum(5)
  private val state = RegInit(UInt(3.W), idle)
  private val received = RegInit(0.U(LogBurstLen.W))
  private val willDrop = RegInit(0.B)

  private val addr       = RegInit(0.U(alen.W))
  private val addrOffset = addr(Offset - 1, 1)
  private val addrIndex  = addr(Index + Offset - 1, Offset)
  private val addrTag    = addr(alen - 1, Index + Offset)
  private val memAddr    = addr(alen - 1, Offset) ## 0.U(Offset.W)

  private val ARVALID = RegInit(0.B); private val RREADY  = RegInit(0.B)
  ICacheMemIODefault(io.memIO, ARVALID, memAddr, RREADY)

  private val ramValid = SyncReadRegs(1, IndexSize, Associativity)
  private val ramTag   = SyncReadRegs(Tag, IndexSize, Associativity)
  private val ramData  = SinglePortRam(clock, BlockSize * 8, IndexSize, Associativity)

  private val hit = WireDefault(0.B)
  private val grp = WireDefault(0.U(log2Ceil(Associativity).W))
  private val wen = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  private val ren = { var x = 1.B; for (i <- wen.indices) { x = x | ~wen(i) }; x }

  private val valid = ramValid.read(addrIndex, ren)
  private val tag   = ramTag  .read(addrIndex, ren)
  private val data  = ramData .read(addrIndex, ren)

  private val way = RegInit(0.U(log2Ceil(Associativity).W))
  private val writeBuffer = RegInit(VecInit(Seq.fill(BurstLen - 1)(0.U(xlen.W))))
  private val wordData = VecInit((0 until BlockSize / 2 - 1).map { i =>
    data(grp)(i * 16 + 31, i * 16)
  } :+ 0.U(16.W) ## data(grp)((BlockSize / 2 - 1) * 16 + 15, (BlockSize / 2 - 1) * 16))

  private val wdata     = io.memIO.r.bits.data ## writeBuffer.asUInt
  private val vecWvalid = VecInit(Seq.fill(Associativity)(1.U))
  private val vecWtag   = VecInit(Seq.fill(Associativity)(addrTag))
  private val vecWdata  = VecInit(Seq.fill(Associativity)(wdata))

  ramValid.write(addrIndex, vecWvalid, wen)
  ramTag  .write(addrIndex, vecWtag  , wen)
  ramData .write(addrIndex, vecWdata , wen)

  io.cpuIO.cpuResult.ready := hit
  io.cpuIO.cpuResult.data  := wordData(addrOffset)

  private val isPeripheral = IsPeripheral(io.cpuIO.cpuReq.addr)
  private val passThrough  = PassThrough(true)(io.memIO, 0.B, addr, 0.U, 0.U, 0.B)

  when(io.cpuIO.cpuReq.valid && state =/= allocate) { addr := Mux(state === passing && !isPeripheral, addr, io.cpuIO.cpuReq.addr) }

  io.inv.ready := 0.B
  when(state === idle) {
    when(io.cpuIO.cpuReq.valid) {
      state := starting
      when(isPeripheral) { state := passing }
    }.elsewhen(io.inv.valid) { ramValid.reset; io.inv.ready := 1.B }
  }
  when(state === starting) { state := compare }
  when(state === compare) {
    ARVALID := 1.B
    state   := allocate
    hit := VecInit(Seq.tabulate(Associativity)(i => valid(i) && tag(i) === addrTag)).asUInt.orR
    grp := Mux1H(Seq.tabulate(Associativity)(i => (valid(i) && tag(i) === addrTag) -> i.U))
    way := MuxLookup(0.B, rand, valid zip Seq.tabulate(Associativity)(_.U))
    when(hit) {
      state := idle
      ARVALID := 0.B
      when(io.cpuIO.cpuReq.valid) {
        state := starting
        when(isPeripheral) { state := passing }
      }
    }
  }
  when(state === allocate) {
    when(io.memIO.r.fire()) {
      when(received === (BurstLen - 1).U) {
        received := 0.U
        state    := idle
        wen(way) := 1.B
        RREADY   := 0.B
      }.otherwise {
        received              := received + 1.U
        writeBuffer(received) := io.memIO.r.bits.data
      }
    }.elsewhen(io.memIO.ar.fire()) {
      ARVALID := 0.B
      RREADY  := 1.B
    }
  }
  when(state === passing) {
    hit := Mux(willDrop || io.jmpBch, 0.B, passThrough.finish)
    passThrough.valid := !io.jmpBch && !passThrough.finish
    io.cpuIO.cpuResult.data := VecInit(Seq.tabulate(xlen / 32)(x => passThrough.rdata(x * 32 + 31, x * 32)))(if (xlen == 32) 0.U else addr(axSize - 1, 2))
    when(passThrough.finish) {
      state := idle
      willDrop := 0.B
      when(io.cpuIO.cpuReq.valid) {
        state := starting
        when(isPeripheral) { state := passing }
      }
    }.elsewhen(io.jmpBch && (!passThrough.ready || !io.cpuIO.cpuReq.valid)) { willDrop := 1.B }
  }

  when(io.jmpBch && state <= compare) { ARVALID := 0.B; state := idle }
}

object ICache {
  def apply()(implicit p: Parameters): ICache = {
    val t = Module(new ICache)
    t.io.memIO.aw := DontCare
    t.io.memIO.w  := DontCare
    t.io.memIO.b  := DontCare
    t
  }
}

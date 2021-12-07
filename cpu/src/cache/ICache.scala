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
  private val idle::starting::compare::allocate::answering::passing::Nil = Enum(6)
  private val state = RegInit(UInt(3.W), idle)
  private val received = RegInit(0.U(LogBurstLen.W))
  private val willDrop = RegInit(0.B)

  private val addr       = RegInit(0.U(alen.W))
  private val addrOffset = addr(Offset - 1, if (ext('C')) 1 else 2)
  private val addrIndex  = addr(Index + Offset - 1, Offset)
  private val addrTag    = addr(alen - 1, Index + Offset)
  private val memAddr    = addr(alen - 1, Offset) ## 0.U(Offset.W)

  private val ARVALID = RegInit(0.B); private val RREADY  = RegInit(0.B)
  ICacheMemIODefault(io.memIO, ARVALID, memAddr, RREADY)

  private val ramValid = SyncReadRegs(1, IndexSize, Associativity)
  private val ramTag   = SyncReadRegs(Tag, IndexSize, Associativity)
  private val ramData  = SinglePortRam(clock, BlockSize * 8, IndexSize, Associativity)

  private val hit = WireDefault(0.B)
  private val grp = RegInit(0.U(log2Ceil(Associativity).W))
  private val wen = WireDefault(VecInit(Seq.fill(Associativity)(0.B)))
  private val ren = { var x = 1.B; for (i <- wen.indices) { x = x | ~wen(i) }; x }

  private val preValid = ramValid.preRead(addrIndex, ren)
  private val preTag   = ramTag  .preRead(addrIndex, ren)
  private val data     = ramData .read   (addrIndex, ren)

  private val way = RegInit(0.U(log2Ceil(Associativity).W))
  private val writeBuffer = RegInit(VecInit(Seq.fill(BurstLen)(0.U(Buslen.W))))
  private val wordData = if (ext('C')) VecInit((0 until BlockSize / 2 - 1).map { i =>
    data(grp)(i * 16 + 31, i * 16)
  } :+ 0.U(16.W) ## data(grp)((BlockSize / 2 - 1) * 16 + 15, (BlockSize / 2 - 1) * 16))
  else VecInit((0 until BlockSize / 4).map { i => data(grp)(i * 32 + 31, i * 32) })

  private val wdata     = writeBuffer.asUInt
  private val vecWvalid = VecInit(Seq.fill(Associativity)(1.U))
  private val vecWtag   = VecInit(Seq.fill(Associativity)(addrTag))
  private val vecWdata  = VecInit(Seq.fill(Associativity)(wdata))

  ramValid.write(addrIndex, vecWvalid, wen)
  ramTag  .write(addrIndex, vecWtag  , wen)
  ramData .write(addrIndex, vecWdata , wen)

  io.cpuIO.cpuResult.ready := hit
  io.cpuIO.cpuResult.data  := wordData(addrOffset)

  private val isPeripheral = if (FetchFromPeri) IsPeripheral(io.cpuIO.cpuReq.addr) else 0.B
  private val passThrough  = if (FetchFromPeri) PassThrough(true)(io.memIO, 0.B, addr, 0.U, 0.U, 0.B) else null

  private val compareHit = RegInit(0.B)
  private val fakeAnswer = RegInit(0.B)
  when(fakeAnswer) { wen(way) := 1.B; fakeAnswer := 0.B }

  when(io.cpuIO.cpuReq.valid && state =/= allocate) { addr := Mux(state === passing && !isPeripheral, addr, io.cpuIO.cpuReq.addr) }
  private val revoke = io.jmpBch || io.cpuIO.cpuReq.revoke

  io.inv.ready := 0.B
  when(state === idle) {
    when(io.cpuIO.cpuReq.valid) { state := Mux(isPeripheral, passing, starting) }
    .elsewhen(io.inv.valid) { ramValid.reset; io.inv.ready := 1.B }
  }
  when(state === starting) {
    state := Mux(willDrop, idle, compare)
    willDrop := 0.B
    compareHit := VecInit(Seq.tabulate(Associativity)(i => preValid(i) && preTag(i) === addrTag)).asUInt.orR
    grp := Mux1H(Seq.tabulate(Associativity)(i => (preValid(i) && preTag(i) === addrTag) -> i.U))
    way := MuxLookup(0.B, rand, preValid zip Seq.tabulate(Associativity)(_.U))
  }
  when(state === compare) {
    ARVALID := ~compareHit
    state   := allocate
    hit     := compareHit
    when(compareHit) {
      state := idle
      when(io.cpuIO.cpuReq.valid) {
        state := starting
        when(isPeripheral) { state := passing }
      }
    }
  }
  when(state === allocate) {
    when(io.memIO.r.fire()) {
      writeBuffer(received) := io.memIO.r.bits.data
      when(received === (BurstLen - 1).U) {
        received   := 0.U
        state      := Mux(willDrop, idle, answering)
        fakeAnswer := willDrop
        willDrop   := 0.B
        RREADY     := 0.B
      }.otherwise { received := received + 1.U }
    }.elsewhen(io.memIO.ar.fire()) {
      ARVALID := 0.B
      RREADY  := 1.B
    }
  }
  when(state === answering) {
    wen(way) := 1.B
    hit := ~willDrop
    willDrop := 0.B
    io.cpuIO.cpuResult.data := (if (ext('C')) VecInit((0 until BlockSize / 2 - 1).map { i =>
      writeBuffer.asUInt()(i * 16 + 31, i * 16)
    } :+ 0.U(16.W) ## writeBuffer.asUInt()((BlockSize / 2 - 1) * 16 + 15, (BlockSize / 2 - 1) * 16))
    else VecInit((0 until BlockSize / 4).map { i => writeBuffer.asUInt()(i * 32 + 31, i * 32) }))(addrOffset)
    state := Mux(willDrop, idle, Mux(io.cpuIO.cpuReq.valid, Mux(isPeripheral, passing, starting), idle))
  }
  if (FetchFromPeri) when(state === passing) {
    hit := Mux(willDrop || revoke, 0.B, passThrough.finish)
    passThrough.valid := !revoke && !passThrough.finish
    io.cpuIO.cpuResult.data := VecInit(Seq.tabulate(xlen / 32)(x => passThrough.rdata(x * 32 + 31, x * 32)))(if (xlen == 32) 0.U else addr(axSize - 1, 2))
    when(passThrough.finish) {
      state := idle
      willDrop := 0.B
      when(io.cpuIO.cpuReq.valid) {
        state := starting
        when(isPeripheral) { state := passing }
      }
    }.elsewhen(revoke && (!passThrough.ready || !io.cpuIO.cpuReq.valid)) { willDrop := 1.B }
  }

  when(revoke) {
     when(state <= compare) { ARVALID := 0.B; state := idle }
     .elsewhen(state === allocate || state === answering) { willDrop := 1.B }
  }
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

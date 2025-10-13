package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

private class SyncReadReg[T <: Data](bits: T = UInt(128.W), wordDepth: Int = 64)(implicit val p: Parameters) extends Module with PrefixParams {
  val io = IO(new Bundle {
    val Q   = Output(bits.cloneType)
    val WEN = Input (Bool())
    val A   = Input (UInt(log2Ceil(wordDepth).W))
    val D   = Input (bits.cloneType)
    val RST = Input (Bool()) // reset
    val PQ  = Output(bits.cloneType) // pre-Q, which means no delay
  })
  override val desiredName = YQModulePrefix + this.getClass().getSimpleName()

  private val sreg = if (bits.isLit) RegInit(VecInit(Seq.fill(wordDepth)(bits))) else Reg(Vec(wordDepth, bits))
  when(io.WEN) { sreg(io.A) := io.D }
  when(io.RST) { sreg := 0.U.asTypeOf(sreg) }
  io.Q  := RegNext(sreg(io.A))
  io.PQ := sreg(io.A)
}

private class SyncReadRegWrapper[T <: Data](bits: T = UInt(128.W), wordDepth: Int = 64)(implicit p: Parameters) {
  private val sregs = Module(new SyncReadReg(bits, wordDepth))
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sregs.io.WEN := 0.B
  sregs.io.A   := rAddr
  sregs.io.D   := 0.U
  sregs.io.RST := 0.B

  def read(x: UInt, en: Bool = 1.B): T = { rAddr := x; sregs.io.Q }

  def write(idx: UInt, data: T, wen: Bool): Unit = {
    wAddr        := idx
    sregs.io.WEN := wen
    sregs.io.D   := data
    when(sregs.io.WEN) { sregs.io.A := wAddr }
  }

  def preRead: T = sregs.io.PQ

  def preRead(x: UInt, en: Bool = 1.B): T = { rAddr := x; sregs.io.PQ }

  def reset: Unit = sregs.io.RST := 1.B
}

class SyncReadRegs[T <: Data](bits: T = UInt(128.W), wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters) {
  private val Regs = Seq.fill(associativity)(new SyncReadRegWrapper(bits, wordDepth))

  def read(x: UInt, en: Bool = 1.B): Vec[T] = VecInit(Seq.tabulate(associativity)(y => Regs(y).read(x, en)))
  def write(idx: UInt, data: T, mask: Vec[Bool]): Unit = for (i <- Regs.indices) { Regs(i).write(idx, data, mask(i)) }
  def preRead: Vec[T] = VecInit(Seq.tabulate(associativity)(y => Regs(y).preRead))
  def preRead(x: UInt, en: Bool = 1.B): Vec[T] = VecInit(Seq.tabulate(associativity)(y => Regs(y).preRead(x, en)))
  def reset: Unit = Regs.foreach(_.reset)
}

object SyncReadRegs {
  def apply[T <: Data](bits: T = UInt(128.W), wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters): SyncReadRegs[T] = new SyncReadRegs(bits, wordDepth, associativity)
}

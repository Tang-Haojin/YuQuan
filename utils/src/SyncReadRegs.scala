package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

private class SyncReadReg(bits: Int = 128, wordDepth: Int = 64, noInit: Boolean = false)(implicit val p: Parameters) extends Module with PrefixParams {
  val io = IO(new Bundle {
    val Q   = Output(UInt(bits.W))
    val WEN = Input (Bool())
    val A   = Input (UInt(log2Ceil(wordDepth).W))
    val D   = Input (UInt(bits.W))
    val RST = Input (Bool()) // reset
    val PQ  = Output(UInt(bits.W)) // pre-Q, which means no delay
  })
  override val desiredName = modulePrefix + this.getClass().getSimpleName()

  private val sreg = if (noInit) Reg(Vec(wordDepth, UInt(bits.W))) else RegInit(VecInit(Seq.fill(wordDepth)(0.U(bits.W))))
  when(io.WEN) { sreg(io.A) := io.D }
  when(io.RST) { sreg := 0.U.asTypeOf(sreg) }
  io.Q  := RegNext(sreg(io.A))
  io.PQ := sreg(io.A)
}

private class SyncReadRegWrapper(bits: Int = 128, wordDepth: Int = 64, noInit: Boolean = false)(implicit p: Parameters) {
  private val sregs = Module(new SyncReadReg(bits, wordDepth, noInit))
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sregs.io.WEN := 0.B
  sregs.io.A   := rAddr
  sregs.io.D   := 0.U
  sregs.io.RST := 0.B

  def read(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sregs.io.Q }

  def write(idx: UInt, data: UInt, wen: Bool): Unit = {
    wAddr        := idx
    sregs.io.WEN := wen
    sregs.io.D   := data
    when(!sregs.io.WEN) { sregs.io.A := wAddr }
  }

  def preRead: UInt = sregs.io.PQ

  def preRead(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sregs.io.PQ }

  def reset: Unit = sregs.io.RST := 1.B
}

class SyncReadRegs(bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4, noInit: Boolean = false)(implicit p: Parameters) {
  private val Regs = Seq.fill(associativity)(new SyncReadRegWrapper(bits, wordDepth, noInit))

  def read(x: UInt, en: Bool = 1.B): Vec[UInt] = VecInit(Seq.tabulate(associativity)(y => Regs(y).read(x, en)))
  def write(idx: UInt, data: UInt, mask: Vec[Bool]): Unit = for (i <- Regs.indices) { Regs(i).write(idx, data, mask(i)) }
  def preRead: Vec[UInt] = VecInit(Seq.tabulate(associativity)(y => Regs(y).preRead))
  def preRead(x: UInt, en: Bool = 1.B): Vec[UInt] = VecInit(Seq.tabulate(associativity)(y => Regs(y).preRead(x, en)))
  def reset: Unit = Regs.foreach(_.reset)
}

object SyncReadRegs {
  def apply(bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4, noInit: Boolean = false)(implicit p: Parameters): SyncReadRegs = new SyncReadRegs(bits, wordDepth, associativity, noInit)
}

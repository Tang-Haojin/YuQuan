package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

private class SyncReadReg(bits: Int = 128, wordDepth: Int = 64)(implicit val p: Parameters) extends Module with PrefixParams {
  val io = IO(new Bundle {
    val Q   = Output(UInt(bits.W))
    val WEN = Input (Bool())
    val A   = Input (UInt(log2Ceil(wordDepth).W))
    val D   = Input (UInt(bits.W))
  })
  override val desiredName = modulePrefix + this.getClass().getSimpleName()

  private val sreg = RegInit(VecInit(Seq.fill(wordDepth)(0.U(bits.W))))
  when(io.WEN) { sreg(io.A) := io.D }
  io.Q := RegNext(sreg(io.A))
}

private object SyncReadReg {
  def apply(bits: Int = 128, wordDepth: Int = 64)(implicit p: Parameters): SyncReadReg = Module(new SyncReadReg(bits, wordDepth))
}

private class SyncReadRegWrapper(bits: Int = 128, wordDepth: Int = 64)(implicit p: Parameters) {
  private val sregs = SyncReadReg(bits, wordDepth)
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sregs.io.WEN := 0.B
  sregs.io.A   := rAddr
  sregs.io.D   := 0.U

  def read(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sregs.io.Q }

  def write(idx: UInt, data: UInt, wen: Bool): Unit = {
    wAddr        := idx
    sregs.io.WEN := wen
    sregs.io.D   := data
    when(!sregs.io.WEN) { sregs.io.A := wAddr }
  }
}

private object SyncReadRegWrapper {
  def apply(bits: Int = 128, wordDepth: Int = 64)(implicit p: Parameters): SyncReadRegWrapper = new SyncReadRegWrapper(bits, wordDepth)
}

class SyncReadRegs(bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters) {
  private val Regs = Seq.fill(associativity)(SyncReadRegWrapper(bits, wordDepth))

  def read(x: UInt, en: Bool = 1.B): Vec[UInt] = {
    VecInit(Seq.tabulate(associativity)(y => { Regs(y).read(x, en) }))
  }

  def write(idx: UInt, data: Vec[UInt], mask: Vec[Bool]): Unit = {
    for (i <- Regs.indices) { Regs(i).write(idx, data(i), mask(i)) }
  }
}

object SyncReadRegs {
  def apply(bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters): SyncReadRegs = new SyncReadRegs(bits, wordDepth, associativity)
}
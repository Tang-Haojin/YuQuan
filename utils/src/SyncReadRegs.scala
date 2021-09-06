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
  override val desiredName = modulePrefix + bits.toString + "_" + wordDepth.toString + "_" + this.getClass().getSimpleName()

  private val ram = RegInit(VecInit(Seq.fill(wordDepth)(0.U(bits.W))))
  private val Q   = RegInit(0.U(bits.W)); io.Q := Q
  when(io.WEN) { ram(io.A) := io.D }
  Q := ram(io.A)
}

private object SyncReadReg {
  def apply(bits: Int = 128, wordDepth: Int = 64)(implicit p: Parameters): SyncReadReg = Module(new SyncReadReg(bits, wordDepth))
}

private class SyncReadRegWrapper(bits: Int = 128, wordDepth: Int = 64)(implicit p: Parameters) {
  private val sram  = SyncReadReg(bits, wordDepth)
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sram.io.WEN := 0.B
  sram.io.A   := rAddr
  sram.io.D   := 0.U

  def read(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sram.io.Q }

  def write(idx: UInt, data: UInt, wen: Bool): Unit = {
    wAddr       := idx
    sram.io.WEN := wen
    sram.io.D   := data
    when(!sram.io.WEN) { sram.io.A := wAddr }
  }
}

private object SyncReadRegWrapper {
  def apply(bits: Int = 128, wordDepth: Int = 64)(implicit p: Parameters): SyncReadRegWrapper = new SyncReadRegWrapper(bits, wordDepth)
}

class SyncReadRegs(bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters) {
  private val SRAMs = Seq.fill(associativity)(SyncReadRegWrapper(bits, wordDepth))

  def read(x: UInt, en: Bool = 1.B): Vec[UInt] = {
    VecInit(Seq.tabulate(associativity)(y => { SRAMs(y).read(x, en) }))
  }

  def write(idx: UInt, data: Vec[UInt], mask: Vec[Bool]): Unit = {
    for (i <- SRAMs.indices) { SRAMs(i).write(idx, data(i), mask(i)) }
  }
}

object SyncReadRegs {
  def apply(bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters): SyncReadRegs = new SyncReadRegs(bits, wordDepth, associativity)
}

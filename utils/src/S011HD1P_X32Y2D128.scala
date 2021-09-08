package utils

import chisel3._
import chisel3.util._

class S011HD1P_X32Y2D128(bits: Int = 128, wordDepth: Int = 64) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val Q   = Output(UInt(bits.W))
    val CLK = Input (Clock())
    val CEN = Input (Bool())
    val WEN = Input (Bool())
    val A   = Input (UInt(log2Ceil(wordDepth).W))
    val D   = Input (UInt(bits.W))
  })
  this.setInline("S011HD1P_X32Y2D128.v", s"""
    |module S011HD1P_X32Y2D128 (
    |  output reg [${bits - 1}:0]                Q,
    |  input                                     CLK,
    |  input                                     CEN,
    |  input                                     WEN,
    |  input      [${log2Ceil(wordDepth) - 1}:0] A,
    |  input      [${bits - 1}:0]                D
    |);
    |
    |  reg [${bits - 1}:0] ram [0:${wordDepth - 1}];
    |  always @(posedge CLK) begin
    |    if (!CEN && !WEN) begin
    |      ram[A] <= D;
    |    end
    |    Q <= !CEN && WEN ? ram[A] : {${bits / 32}{32'hdead_feed}};
    |  end
    |
    |endmodule
    |""".stripMargin)
}

object S011HD1P_X32Y2D128 {
  def apply(bits: Int = 128, wordDepth: Int = 64): S011HD1P_X32Y2D128 = Module(new S011HD1P_X32Y2D128(bits, wordDepth))
}

class SramWrapper(clock: Clock, bits: Int = 128, wordDepth: Int = 64) {
  private val sram = S011HD1P_X32Y2D128(bits, wordDepth)
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sram.io.CLK := clock
  sram.io.CEN := 0.B
  sram.io.WEN := 1.B
  sram.io.A   := rAddr
  sram.io.D   := 0.U

  def read(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sram.io.Q }

  def write(idx: UInt, data: UInt, wen: Bool): Unit = {
    wAddr       := idx
    sram.io.WEN := !wen
    sram.io.D   := data
    when(!sram.io.WEN) { sram.io.A := wAddr }
  }
}

object SramWrapper {
  def apply(clock: Clock, bits: Int = 128, wordDepth: Int = 64): SramWrapper = new SramWrapper(clock, bits, wordDepth)
}

class SinglePortRam(clock: Clock, bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4) extends ReadWriteInterface {
  private val SRAMs = Seq.fill(associativity)(SramWrapper(clock, bits, wordDepth))

  def read(x: UInt, en: Bool = 1.B): Vec[UInt] = {
    VecInit(Seq.tabulate(associativity)(y => { SRAMs(y).read(x, en) }))
  }

  def write(idx: UInt, data: Vec[UInt], mask: Seq[Bool]): Unit = {
    for (i <- SRAMs.indices) { SRAMs(i).write(idx, data(i), mask(i)) }
  }
}

object SinglePortRam {
  def apply(clock: Clock, bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4): SinglePortRam = new SinglePortRam(clock, bits, wordDepth, associativity)
}

class NoCacheRam(associativity: Int = 4) extends ReadWriteInterface {
  def read(x: UInt, en: Bool = 1.B): Vec[UInt] = VecInit(Seq.fill(associativity)(0.U))
  def write(idx: UInt, data: Vec[UInt], mask: Seq[Bool]): Unit = {}
}

object NoCacheRam {
  def apply(associativity: Int = 4): NoCacheRam = new NoCacheRam(associativity)
}

abstract private[utils] class ReadWriteInterface {
  def read(x: UInt, en: Bool = 1.B): Vec[UInt]
  def write(idx: UInt, data: Vec[UInt], mask: Seq[Bool]): Unit
}

package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

class S011HD1P_SRAM(bits: Int = 128, wordDepth: Int = 64) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val Q   = Output(UInt(bits.W))
    val CLK = Input (Clock())
    val CEN = Input (Bool())
    val WEN = Input (Bool())
    val A   = Input (UInt(log2Ceil(wordDepth).W))
    val D   = Input (UInt(bits.W))
  })
  override val desiredName = s"S011HD1P_X${wordDepth / 2}Y2D${bits}"
  this.setInline(s"${desiredName}.v",
  s"""|module ${desiredName}(
      |  output reg [${bits - 1}:0]                Q,
      |  input                                     CLK,
      |  input                                     CEN,
      |  input                                     WEN,
      |  input      [${log2Ceil(wordDepth) - 1}:0] A,
      |  input      [${bits - 1}:0]                D
      |);
      |  reg [${bits - 1}:0] ram [0:${wordDepth - 1}];
      |  always @(posedge CLK) begin
      |    if (!CEN && !WEN) begin
      |      ram[A] <= D;
      |    end
      |    Q <= !CEN && WEN ? ram[A] : {${bits / 32}{32'hdead_feed}};
      |  end
      |endmodule
      |""".stripMargin)
}

class S011HD1P_BW_SRAM(bits: Int = 128, wordDepth: Int = 64) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val Q    = Output(UInt(bits.W))
    val CLK  = Input (Clock())
    val CEN  = Input (Bool())
    val WEN  = Input (Bool())
    val BWEN = Input (UInt(bits.W))
    val A    = Input (UInt(log2Ceil(wordDepth).W))
    val D    = Input (UInt(bits.W))
  })
  override val desiredName = s"S011HD1P_X${wordDepth / 2}Y2D${bits}_BW"
  this.setInline(s"${desiredName}.v",
  s"""|module ${desiredName}(
      |  output reg [${bits - 1}:0]                Q,
      |  input                                     CLK,
      |  input                                     CEN,
      |  input                                     WEN,
      |  input      [${bits - 1}:0]                BWEN,
      |  input      [${log2Ceil(wordDepth) - 1}:0] A,
      |  input      [${bits - 1}:0]                D
      |);
      |  reg [${bits - 1}:0] ram [0:${wordDepth - 1}];
      |  always @(posedge CLK) begin
      |    if (!CEN && !WEN) begin
      |      ram[A] <= (D & ~BWEN) | (ram[A] & BWEN);
      |    end
      |    Q <= !CEN && WEN ? ram[A] : {${bits / 32}{32'hdead_feed}};
      |  end
      |endmodule
      |""".stripMargin)
}

class bytewrite_ram_1b(bits: Int = 128, wordDepth: Int = 64) extends BlackBox(Map(
  "SIZE" -> wordDepth,
  "ADDR_WIDTH" -> log2Ceil(wordDepth),
  "COL_WIDTH" -> 8,
  "NB_COL" -> bits / 8
)) with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk  = Input (Clock())
    val we   = Input (UInt((bits / 8).W))
    val addr = Input (UInt(log2Ceil(wordDepth).W))
    val din  = Input (UInt(bits.W))
    val dout = Output(UInt(bits.W))
  })
  this.setInline("bytewrite_ram_1b.v",
  s"""|// Single-Port BRAM with Byte-wide Write Enable
      |// Read-First mode
      |// Single-process description
      |// Compact description of the write with a generate-for 
      |//   statement
      |// Column width and number of columns easily configurable
      |//
      |// bytewrite_ram_1b.v
      |//
      |
      |module bytewrite_ram_1b (clk, we, addr, din, dout);
      |
      |parameter SIZE = 1024;
      |parameter ADDR_WIDTH = 10;
      |parameter COL_WIDTH = 8;
      |parameter NB_COL = 4;
      |
      |input clk;
      |input [NB_COL-1:0] we;
      |input [ADDR_WIDTH-1:0] addr;
      |input [NB_COL*COL_WIDTH-1:0] din;
      |output reg [NB_COL*COL_WIDTH-1:0] dout;
      |
      |reg [NB_COL*COL_WIDTH-1:0] RAM [SIZE-1:0];
      |
      |always @(posedge clk)
      |begin
      |    dout <= RAM[addr];
      |end
      |
      |generate genvar i;
      |for (i = 0; i < NB_COL; i = i+1)
      |begin
      |always @(posedge clk)
      |begin
      |    if (we[i])
      |        RAM[addr][(i+1)*COL_WIDTH-1:i*COL_WIDTH] <= din[(i+1)*COL_WIDTH-1:i*COL_WIDTH];
      |    end
      |end
      |endgenerate
      |
      |endmodule
      |""".stripMargin)
}

private[utils] class S011HD1P_SramWrapper(clock: Clock, bits: Int = 128, wordDepth: Int = 64) extends SramWrapperInterface {
  private val sram = Module(new S011HD1P_SRAM(bits, wordDepth))
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
    when(wen) { sram.io.A := wAddr }
  }

  def write(idx: UInt, data: UInt, wen: Bool, bytewrite: UInt): Unit =
    require(false, "Bytewrite is not support for this type of ram.")
}

private[utils] class S011HD1P_BW_SramWrapper(clock: Clock, bits: Int = 128, wordDepth: Int = 64) extends SramWrapperInterface {
  private val sram = Module(new S011HD1P_BW_SRAM(bits, wordDepth))
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sram.io.CLK  := clock
  sram.io.CEN  := 0.B
  sram.io.WEN  := 1.B
  sram.io.BWEN := Fill(bits, 1.B)
  sram.io.A    := rAddr
  sram.io.D    := 0.U

  def read(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sram.io.Q }

  def write(idx: UInt, data: UInt, wen: Bool): Unit = {
    wAddr        := idx
    sram.io.WEN  := !wen
    sram.io.BWEN := 0.U
    sram.io.D    := data
    when(wen) { sram.io.A := wAddr }
  }

  def write(idx: UInt, data: UInt, wen: Bool, bytewrite: UInt): Unit = {
    wAddr        := idx
    sram.io.WEN  := !wen
    sram.io.BWEN := Cat((~bytewrite).asBools().map(Fill(8, _)).reverse)
    sram.io.D    := data
    when(wen) { sram.io.A := wAddr }
  }
}

private[utils] class bytewrite_ram_1b_SramWrapper(clock: Clock, bits: Int = 128, wordDepth: Int = 64) extends SramWrapperInterface {
  private val sram = Module(new bytewrite_ram_1b(bits, wordDepth))
  private val rAddr = WireDefault(0.U(log2Ceil(wordDepth).W))
  private val wAddr = WireDefault(0.U(log2Ceil(wordDepth).W))

  sram.io.clk  := clock
  sram.io.we   := 0.U
  sram.io.addr := rAddr
  sram.io.din  := 0.U

  def read(x: UInt, en: Bool = 1.B): UInt = { rAddr := x; sram.io.dout }

  def write(idx: UInt, data: UInt, wen: Bool): Unit = {
    wAddr       := idx
    sram.io.we  := Fill(bits / 8, wen)
    sram.io.din := data
    when(wen) { sram.io.addr := wAddr }
  }

  def write(idx: UInt, data: UInt, wen: Bool, bytewrite: UInt): Unit = {
    wAddr       := idx
    sram.io.we  := Fill(bits / 8, wen) & bytewrite
    sram.io.din := data
    when(wen) { sram.io.addr := wAddr }
  }
}

abstract private[utils] class SramWrapperInterface {
  def read(x: UInt, en: Bool = 1.B): UInt
  def write(idx: UInt, data: UInt, wen: Bool): Unit
  def write(idx: UInt, data: UInt, wen: Bool, bytewrite: UInt): Unit
}

class SinglePortRam(clock: Clock, bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4)(implicit val p: Parameters) extends ReadWriteInterface with UtilsParams {
  private val SRAMs = Seq.fill(associativity)(if (useXilinx) new bytewrite_ram_1b_SramWrapper(clock, bits, wordDepth) else new S011HD1P_BW_SramWrapper(clock, bits, wordDepth))

  def read(x: UInt, en: Bool = 1.B): Vec[UInt] =
    VecInit(Seq.tabulate(associativity)(SRAMs(_).read(x, en)))

  def write(idx: UInt, data: UInt, mask: Seq[Bool]): Unit =
    for (i <- SRAMs.indices) SRAMs(i).write(idx, data, mask(i))

  def write(idx: UInt, data: UInt, mask: Seq[Bool], bytewrite: UInt): Unit =
    for (i <- SRAMs.indices) SRAMs(i).write(idx, data, mask(i), bytewrite)
}

object SinglePortRam {
  def apply(clock: Clock, bits: Int = 128, wordDepth: Int = 64, associativity: Int = 4)(implicit p: Parameters): SinglePortRam =
    new SinglePortRam(clock, bits, wordDepth, associativity)
}

abstract private[utils] class ReadWriteInterface {
  def read(x: UInt, en: Bool = 1.B): Vec[UInt]
  def write(idx: UInt, data: UInt, mask: Seq[Bool]): Unit
  def write(idx: UInt, data: UInt, mask: Seq[Bool], bytewrite: UInt): Unit
}

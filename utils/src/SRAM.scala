package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

class SRAM(bits: Int = 128, wordDepth: Int = 64, addrWidth: Int = 6)(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with PrefixParams {
  val io = IO(new Bundle {
    val Q   = Output(UInt(bits.W))
    val CLK = Input (Clock())
    val CEN = Input (Bool())
    val WEN = Input (Bool())
    val A   = Input (UInt(addrWidth.W))
    val D   = Input (UInt(bits.W))
  })
  override val desiredName = modulePrefix + this.getClass().getSimpleName()
  this.setInline(desiredName + ".v", s"""
    |module ${desiredName} (
    |  output reg [${bits - 1}:0]      Q,
    |  input                           CLK,
    |  input                           CEN,
    |  input                           WEN,
    |  input      [${addrWidth - 1}:0] A,
    |  input      [${bits - 1}:0]      D
    |);
    |
    |  reg [${bits - 1}:0] ram [0:${wordDepth - 1}];
    |  always @(posedge CLK) begin
    |    if (!CEN && !CEN) begin
    |      ram[A] <= D;
    |    end
    |    Q <= !CEN && WEN ? ram[A] : {${bits / 32}{32'hdead_feed}};
    |  end
    |
    |endmodule
  """)
}

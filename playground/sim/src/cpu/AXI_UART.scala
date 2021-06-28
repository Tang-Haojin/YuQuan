package sim

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.config.GeneralConfig._

class UartGetc extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val getc  = Input (Bool())
    val ch    = Output(UInt(8.W))
  })

  setInline("UartGetc.v",s"""
    |import "DPI-C" function void uart_getc(output byte ch);
    |
    |module UartGetc (
    |  input  clock,
    |  input  getc,
    |  output reg [7:0] ch
    |);
    |
    |  always@(posedge clock) begin
    |    if (getc) uart_getc(ch);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class UART extends RawModule {
  val io = IO(new AxiSlaveIO)

  io.axiWr.BID := 1.U // since only cpu requests writing now
  io.axiWr.BRESP := DontCare
  io.axiWr.BUSER := DontCare

  io.axiRd.RLAST := 1.B
  io.axiRd.RUSER := DontCare
  io.axiRd.RRESP := DontCare

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(1.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
                                io.axiRa.ARREADY := 1.B
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(4.W)); io.axiRd.RID := RID
    val WDATA  = RegInit(0.U(XLEN.W))
    val RDATA  = RegInit(0.U(8.W))
    
    io.axiRd.RDATA := Cat(Fill(XLEN - 8, 0.U), RDATA)

    val uart_getc = Module(new UartGetc)
    uart_getc.io.clock := io.basic.ACLK
    uart_getc.io.getc  := 0.B

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      uart_getc.io.getc := 1.B
      RID := io.axiRa.ARID
      RVALID := 1.B
      RDATA := uart_getc.io.ch
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWREADY := 0.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      WDATA  := io.axiWd.WDATA
      WREADY := 0.B
    }

    when(~io.axiWa.AWREADY && ~io.axiWd.WREADY) {
      AWREADY := 1.B
      WREADY  := 1.B
      printf("%c", WDATA(7, 0))
      BVALID  := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }
  }
}

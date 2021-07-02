package sim

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.config.GeneralConfig._

class UartRead extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val getc  = Input (Bool())
    val addr  = Input (UInt(8.W))
    val ch    = Output(UInt(8.W))
  })

  setInline("UartRead.v",s"""
    |import "DPI-C" function void uart_read(input byte addr, output byte ch);
    |
    |module UartRead (
    |  input  clock,
    |  input  getc,
    |  input  [7:0] addr,
    |  output reg [7:0] ch
    |);
    |
    |  always@(posedge clock) begin
    |    if (getc) uart_read(addr, ch);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class UartWrite extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val wen   = Input (Bool())
    val waddr = Input (UInt(8.W))
    val wdata = Input (UInt(8.W))
  })

  setInline("UartWrite.v", s"""
    |import "DPI-C" function void uart_write(input byte addr, input byte data);
    |
    |module UartWrite (
    |  input clock,
    |  input wen,
    |  input [7:0] waddr,
    |  input [7:0] wdata
    |);
    |
    |  always@(posedge clock) begin
    |    if (wen) uart_write(waddr, wdata);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class UartInt extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val inter = Output(Bool())
  })

  setInline("UartInt.v", s"""
    |import "DPI-C" function void uart_int(output bit inter);
    |
    |module UartInt (
    |  input  clock,
    |  output inter
    |);
    |
    |  always@(posedge clock) begin
    |    uart_int(inter);
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

    val uart_read = Module(new UartRead)
    uart_read.io.clock := io.basic.ACLK
    uart_read.io.getc  := 0.B
    uart_read.io.addr  := io.axiRa.ARADDR - UART0_MMIO.UART0_BASE.U

    val uart_write = Module(new UartWrite)
    uart_write.io.clock := io.basic.ACLK
    uart_write.io.wen   := 0.B
    uart_write.io.waddr := io.axiWa.AWADDR - UART0_MMIO.UART0_BASE.U
    uart_write.io.wdata := io.axiWd.WDATA

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      uart_read.io.getc := 1.B
      RID := io.axiRa.ARID
      RVALID := 1.B
      RDATA := uart_read.io.ch
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
      uart_write.io.wen := 1.B
      BVALID  := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }
  }
}

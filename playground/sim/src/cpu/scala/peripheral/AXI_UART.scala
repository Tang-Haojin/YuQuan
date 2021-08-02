package sim

import chisel3._
import chisel3.util._

import tools._
import cpu.config.GeneralConfig._
import cpu.peripheral._

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

class UartWrapperIO extends AxiSlaveIO {
  val interrupt = Output(Bool())    // interrupt request (active-high)
}

abstract class UartWrapper extends RawModule {
  val io = IO(new UartWrapperIO)
}

class UartSim extends UartWrapper {
  io.axiWr.BRESP := 0.U
  io.axiWr.BUSER := DontCare

  io.axiRd.RLAST := 1.B
  io.axiRd.RUSER := DontCare
  io.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(IDLEN.W)); io.axiRd.RID := RID
    val BID    = RegInit(0.U(IDLEN.W)); io.axiWr.BID := BID
    val ARADDR = RegInit(0.U(3.W))
    val AWADDR = RegInit(0.U(3.W))

    val wireARADDR = WireDefault(UInt(3.W), ARADDR)

    val uart_read = Module(new UartRead)
    uart_read.io.clock := io.basic.ACLK
    uart_read.io.getc  := 0.B
    uart_read.io.addr  := wireARADDR
    io.axiRd.RDATA     := VecInit((0 until 8).map { i => uart_read.io.ch << (8 * i) })(ARADDR)

    val uart_write = Module(new UartWrite)
    uart_write.io.clock := io.basic.ACLK
    uart_write.io.wen   := 0.B
    uart_write.io.waddr := AWADDR
    uart_write.io.wdata := VecInit((0 until 8).map { i => io.axiWd.WDATA >> (8 * i) })(AWADDR)

    val uart_int = Module(new UartInt)
    uart_int.io.clock := io.basic.ACLK
    io.interrupt      := uart_int.io.inter

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      uart_read.io.getc := 1.B
      wireARADDR := io.axiRa.ARADDR
      ARADDR  := wireARADDR
      RID     := io.axiRa.ARID
      ARREADY := 0.B
      RVALID  := 1.B
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWADDR  := io.axiWa.AWADDR
      BID     := io.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      uart_write.io.wen := 1.B
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}

class UartReal extends UartWrapper {
  val uart16550 = Module(new Uart16550)
  val tty       = Module(new TTY)

  tty.io.clock     := io.basic.ACLK
  tty.io.reset     := io.basic.ARESETn
  tty.io.srx       := uart16550.io.stx
  uart16550.io.srx := tty.io.stx

  io.basic <> uart16550.io.basic
  io.axiRa <> uart16550.io.axiRa
  io.axiRd <> uart16550.io.axiRd
  io.axiWa <> uart16550.io.axiWa
  io.axiWd <> uart16550.io.axiWd
  io.axiWr <> uart16550.io.axiWr

  io.interrupt <> uart16550.io.interrupt
}

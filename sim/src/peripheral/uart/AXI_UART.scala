package sim.peripheral.uart

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral.uart16550._
import sim.SimParams

class UartRead(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with SimParams {
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

class UartWrite(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with SimParams {
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

class UartInt(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with SimParams {
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

class UartWrapperIO(implicit p: Parameters) extends AxiSlaveIO {
  val interrupt = Output(Bool())    // interrupt request (active-high)
}

abstract class UartWrapper(implicit p: Parameters) extends RawModule with SimParams {
  val io = IO(new UartWrapperIO)
}

class UartSim(implicit val p: Parameters) extends UartWrapper {
  io.channel.axiWr.BRESP := 0.U
  io.channel.axiWr.BUSER := DontCare

  io.channel.axiRd.RLAST := 1.B
  io.channel.axiRd.RUSER := DontCare
  io.channel.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.channel.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.channel.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.channel.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.channel.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(idlen.W)); io.channel.axiRd.RID := RID
    val BID    = RegInit(0.U(idlen.W)); io.channel.axiWr.BID := BID
    val ARADDR = RegInit(0.U(3.W))
    val AWADDR = RegInit(0.U(3.W))

    val wireARADDR = WireDefault(UInt(3.W), ARADDR)

    val uart_read = Module(new UartRead)
    uart_read.io.clock     := io.basic.ACLK
    uart_read.io.getc      := 0.B
    uart_read.io.addr      := wireARADDR
    io.channel.axiRd.RDATA := VecInit((0 until 8).map { i => uart_read.io.ch << (8 * i) })(ARADDR)

    val uart_write = Module(new UartWrite)
    uart_write.io.clock := io.basic.ACLK
    uart_write.io.wen   := 0.B
    uart_write.io.waddr := AWADDR
    uart_write.io.wdata := VecInit((0 until 8).map { i => io.channel.axiWd.WDATA >> (8 * i) })(AWADDR)

    val uart_int = Module(new UartInt)
    uart_int.io.clock := io.basic.ACLK
    io.interrupt      := uart_int.io.inter

    when(io.channel.axiRd.RVALID && io.channel.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.axiRa.ARVALID && io.channel.axiRa.ARREADY) {
      uart_read.io.getc := 1.B
      wireARADDR := io.channel.axiRa.ARADDR
      ARADDR  := wireARADDR
      RID     := io.channel.axiRa.ARID
      ARREADY := 0.B
      RVALID  := 1.B
    }

    when(io.channel.axiWa.AWVALID && io.channel.axiWa.AWREADY) {
      AWADDR  := io.channel.axiWa.AWADDR
      BID     := io.channel.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
    }

    when(io.channel.axiWd.WVALID && io.channel.axiWd.WREADY) {
      uart_write.io.wen := 1.B
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.channel.axiWr.BVALID && io.channel.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}

class UartReal(implicit val p: Parameters) extends UartWrapper {
  val uart16550 = Module(new Uart16550)
  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val tty = Module(new TTY)
    tty.io.srx       := uart16550.io.stx
    uart16550.io.srx := tty.io.stx
  }

  io.basic <> uart16550.io.basic
  io.channel.axiRa <> uart16550.io.channel.axiRa
  io.channel.axiRd <> uart16550.io.channel.axiRd
  io.channel.axiWa <> uart16550.io.channel.axiWa
  io.channel.axiWd <> uart16550.io.channel.axiWd
  io.channel.axiWr <> uart16550.io.channel.axiWr

  io.interrupt <> uart16550.io.interrupt
}

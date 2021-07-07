package sim

import chisel3._
import chisel3.util._

import tools._
import cpu.config.GeneralConfig._
import chisel3.util.experimental.loadMemoryFromFileInline

class RamRead extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val addr  = Input (UInt(64.W))
    val data  = Output(UInt(64.W))
  })

  setInline("RamRead.v",s"""
    |import "DPI-C" function void ram_read(input longint addr, output longint data);
    |
    |module RamRead (
    |  input  clock,
    |  input  [63:0] addr,
    |  output reg [63:0] data
    |);
    |
    |  always@(posedge clock) begin
    |    ram_read(addr, data);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class RamWrite extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val wen   = Input (Bool())
    val addr  = Input (UInt(64.W))
    val data  = Input (UInt(64.W))
    val mask  = Input (UInt(8.W))
  })

  setInline("RamWrite.v",s"""
    |import "DPI-C" function void ram_write(input longint addr, input longint data, input byte mask);
    |
    |module RamWrite (
    |  input  clock,
    |  input  wen,
    |  input  [63:0] addr,
    |  input  [63:0] data,
    |  input  [ 7:0] mask
    |);
    |
    |  always@(posedge clock) begin
    |    if (wen) ram_write(addr, data, mask);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class RAM extends RawModule {
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
    val ARREADY = RegInit(1.B); io.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(4.W)); io.axiRd.RID := RID
    val ARADDR = RegInit(0.U(XLEN.W))
    val AWADDR = RegInit(0.U(XLEN.W))
    val WDATA  = RegInit(0.U(XLEN.W))
    val WSTRB  = RegInit(0.U((XLEN / 8).W))

    val wireARADDR = WireDefault(UInt(XLEN.W), ARADDR)

    val ram_read = Module(new RamRead)
    ram_read.io.clock := io.basic.ACLK
    ram_read.io.addr  := wireARADDR
    io.axiRd.RDATA    := ram_read.io.data

    val ram_write = Module(new RamWrite)
    ram_write.io.clock := io.basic.ACLK
    ram_write.io.wen   := 0.B
    ram_write.io.addr  := AWADDR
    ram_write.io.data  := WDATA
    ram_write.io.mask  := WSTRB

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      RID := io.axiRa.ARID
      ARADDR := io.axiRa.ARADDR - MEMBase.U
      wireARADDR := io.axiRa.ARADDR - MEMBase.U
      ARREADY := 0.B
      RVALID := 1.B
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWADDR  := io.axiWa.AWADDR - MEMBase.U
      AWREADY := 0.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      WDATA  := io.axiWd.WDATA
      WSTRB  := io.axiWd.WSTRB
      WREADY := 0.B
    }

    when(~io.axiWa.AWREADY && ~io.axiWd.WREADY) {
      AWREADY          := 1.B
      WREADY           := 1.B
      BVALID           := 1.B
      ram_write.io.wen := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }
  }
}

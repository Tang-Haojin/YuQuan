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

  io.axiWr.BRESP := 0.U
  io.axiWr.BUSER := DontCare

  io.axiRd.RLAST := 0.B
  io.axiRd.RUSER := DontCare
  io.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID
    val ARSIZE  = RegInit(0.U(3.W))
    val ARLEN   = RegInit(0.U(8.W))
    val AWSIZE  = RegInit(0.U(3.W))
    val AWLEN   = RegInit(0.U(8.W))

    val RID    = RegInit(0.U(IDLEN.W)); io.axiRd.RID := RID
    val BID    = RegInit(0.U(IDLEN.W)); io.axiWr.BID := BID
    val ARADDR = RegInit(0.U(ALEN.W))
    val AWADDR = RegInit(0.U(ALEN.W))

    val wireARADDR = WireDefault(UInt(ALEN.W), ARADDR)
    val wireRStep  = WireDefault(0.U(128.W))
    val wireWStep  = WireDefault(0.U(128.W))

    for (i <- 0 until 8) {
      when(ARSIZE === i.U) { wireRStep := (1 << i).U }
      when(AWSIZE === i.U) { wireWStep := (1 << i).U }
    }

    val ram_read = Module(new RamRead)
    ram_read.io.clock := io.basic.ACLK
    ram_read.io.addr  := wireARADDR
    io.axiRd.RDATA    := ram_read.io.data

    val ram_write = Module(new RamWrite)
    ram_write.io.clock := io.basic.ACLK
    ram_write.io.wen   := 0.B
    ram_write.io.addr  := AWADDR
    ram_write.io.data  := io.axiWd.WDATA
    ram_write.io.mask  := io.axiWd.WSTRB

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      when(ARLEN === 0.U) {
        RVALID         := 0.B
        ARREADY        := 1.B
        io.axiRd.RLAST := 1.B
      }.otherwise {
        wireARADDR := ARADDR + wireRStep
        ARADDR     := wireARADDR
        ARLEN      := ARLEN - 1.U
      }
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      RID        := io.axiRa.ARID
      wireARADDR := io.axiRa.ARADDR(ALEN - 1, AxSIZE) ## 0.U(AxSIZE.W) - MEMBase.U
      ARADDR     := wireARADDR
      ARREADY    := 0.B
      RVALID     := 1.B
      ARSIZE     := io.axiRa.ARSIZE
      ARLEN      := io.axiRa.ARLEN
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWADDR  := io.axiWa.AWADDR(ALEN - 1, AxSIZE) ## 0.U(AxSIZE.W) - MEMBase.U
      BID     := io.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
      AWSIZE  := io.axiWa.AWSIZE
      AWLEN   := io.axiWa.AWLEN
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      ram_write.io.wen := 1.B
      when(AWLEN === 0.U) {
        WREADY  := 0.B
        BVALID  := 1.B
      }.otherwise {
        AWADDR := AWADDR + wireWStep
        AWLEN  := AWLEN - 1.U
      }
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID := 0.B
    }
  }
}

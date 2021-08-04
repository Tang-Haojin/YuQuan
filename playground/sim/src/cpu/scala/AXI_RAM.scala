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

  io.channel.axiWr.BRESP := 0.U
  io.channel.axiWr.BUSER := DontCare

  io.channel.axiRd.RLAST := 0.B
  io.channel.axiRd.RUSER := DontCare
  io.channel.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.channel.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.channel.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.channel.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.channel.axiRd.RVALID  := RVALID
    val ARSIZE  = RegInit(0.U(3.W))
    val ARLEN   = RegInit(0.U(8.W))
    val AWSIZE  = RegInit(0.U(3.W))
    val AWLEN   = RegInit(0.U(8.W))

    val RID    = RegInit(0.U(IDLEN.W)); io.channel.axiRd.RID := RID
    val BID    = RegInit(0.U(IDLEN.W)); io.channel.axiWr.BID := BID
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
    io.channel.axiRd.RDATA    := ram_read.io.data

    val ram_write = Module(new RamWrite)
    ram_write.io.clock := io.basic.ACLK
    ram_write.io.wen   := 0.B
    ram_write.io.addr  := AWADDR
    ram_write.io.data  := io.channel.axiWd.WDATA
    ram_write.io.mask  := io.channel.axiWd.WSTRB

    when(io.channel.axiRd.RVALID && io.channel.axiRd.RREADY) {
      when(ARLEN === 0.U) {
        RVALID         := 0.B
        ARREADY        := 1.B
        io.channel.axiRd.RLAST := 1.B
      }.otherwise {
        wireARADDR := ARADDR + wireRStep
        ARADDR     := wireARADDR
        ARLEN      := ARLEN - 1.U
      }
    }.elsewhen(io.channel.axiRa.ARVALID && io.channel.axiRa.ARREADY) {
      RID        := io.channel.axiRa.ARID
      wireARADDR := io.channel.axiRa.ARADDR(ALEN - 1, AxSIZE) ## 0.U(AxSIZE.W) - MEMBase.U
      ARADDR     := wireARADDR
      ARREADY    := 0.B
      RVALID     := 1.B
      ARSIZE     := io.channel.axiRa.ARSIZE
      ARLEN      := io.channel.axiRa.ARLEN
    }

    when(io.channel.axiWa.AWVALID && io.channel.axiWa.AWREADY) {
      AWADDR  := io.channel.axiWa.AWADDR(ALEN - 1, AxSIZE) ## 0.U(AxSIZE.W) - MEMBase.U
      BID     := io.channel.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
      AWSIZE  := io.channel.axiWa.AWSIZE
      AWLEN   := io.channel.axiWa.AWLEN
    }

    when(io.channel.axiWd.WVALID && io.channel.axiWd.WREADY) {
      ram_write.io.wen := 1.B
      when(AWLEN === 0.U) {
        WREADY  := 0.B
        BVALID  := 1.B
      }.otherwise {
        AWADDR := AWADDR + wireWStep
        AWLEN  := AWLEN - 1.U
      }
    }

    when(io.channel.axiWr.BVALID && io.channel.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID := 0.B
    }
  }
}

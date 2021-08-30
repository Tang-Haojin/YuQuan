package sim.peripheral.storage

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.config.GeneralConfig._
import cpu.tools._

class StorageRead(implicit p: Parameters) extends YQBlackBox with HasBlackBoxInline {
  val io = IO(new YQBundle {
    val clock = Input (Clock())
    val addr  = Input (UInt(64.W))
    val data  = Output(UInt(64.W))
  })

  setInline("StorageRead.v",s"""
    |import "DPI-C" function longint storage_read(input longint addr);
    |
    |module StorageRead (
    |  input  clock,
    |  input  [63:0] addr,
    |  output reg [63:0] data
    |);
    |
    |  always@(posedge clock) begin
    |    data <= storage_read(addr);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class StorageWrite(implicit p: Parameters) extends YQBlackBox with HasBlackBoxInline {
  val io = IO(new YQBundle {
    val clock = Input (Clock())
    val wen   = Input (Bool())
    val addr  = Input (UInt(64.W))
    val data  = Input (UInt(64.W))
    val mask  = Input (UInt(8.W))
  })

  setInline("StorageWrite.v",s"""
    |import "DPI-C" function void storage_write(input longint addr, input longint data, input byte mask);
    |
    |module StorageWrite (
    |  input  clock,
    |  input  wen,
    |  input  [63:0] addr,
    |  input  [63:0] data,
    |  input  [ 7:0] mask
    |);
    |
    |  always@(posedge clock) begin
    |    if (wen) storage_write(addr, data, mask);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class Storage(implicit p: Parameters) extends YQRawModule {
  val io = IO(new AxiSlaveIO)

  io.channel.axiWr.BRESP := 0.U
  io.channel.axiWr.BUSER := DontCare

  io.channel.axiRd.RLAST := 0.B
  io.channel.axiRd.RUSER := DontCare
  io.channel.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.channel.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.channel.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.channel.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.channel.axiRd.RVALID  := RVALID
    val ARSIZE  = RegInit(0.U(3.W))
    val ARLEN   = RegInit(0.U(8.W))
    val AWSIZE  = RegInit(0.U(3.W))
    val AWLEN   = RegInit(0.U(8.W))

    val RID    = RegInit(0.U(idlen.W)); io.channel.axiRd.RID := RID
    val BID    = RegInit(0.U(idlen.W)); io.channel.axiWr.BID := BID
    val ARADDR = RegInit(0.U(alen.W))
    val AWADDR = RegInit(0.U(alen.W))

    val wireARADDR = WireDefault(UInt(alen.W), ARADDR)
    val wireRStep  = WireDefault(0.U(128.W))
    val wireWStep  = WireDefault(0.U(128.W))

    for (i <- 0 until 8) {
      when(ARSIZE === i.U) { wireRStep := (1 << i).U }
      when(AWSIZE === i.U) { wireWStep := (1 << i).U }
    }

    val storage_read = Module(new StorageRead)
    storage_read.io.clock  := io.basic.ACLK
    storage_read.io.addr   := wireARADDR
    io.channel.axiRd.RDATA := storage_read.io.data

    val storage_write = Module(new StorageWrite)
    storage_write.io.clock := io.basic.ACLK
    storage_write.io.wen   := 0.B
    storage_write.io.addr  := AWADDR
    storage_write.io.data  := io.channel.axiWd.WDATA
    storage_write.io.mask  := io.channel.axiWd.WSTRB

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
      wireARADDR := io.channel.axiRa.ARADDR(alen - 1, axSize) ## 0.U(axSize.W) - STROAGE.BASE.U
      ARADDR     := wireARADDR
      ARREADY    := 0.B
      RVALID     := 1.B
      ARSIZE     := io.channel.axiRa.ARSIZE
      ARLEN      := io.channel.axiRa.ARLEN
    }

    when(io.channel.axiWa.AWVALID && io.channel.axiWa.AWREADY) {
      AWADDR  := io.channel.axiWa.AWADDR(alen - 1, axSize) ## 0.U(axSize.W) - STROAGE.BASE.U
      BID     := io.channel.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
      AWSIZE  := io.channel.axiWa.AWSIZE
      AWLEN   := io.channel.axiWa.AWLEN
    }

    when(io.channel.axiWd.WVALID && io.channel.axiWd.WREADY) {
      storage_write.io.wen := 1.B
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
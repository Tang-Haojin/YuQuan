package sim.peripheral.storage

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
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

  io.channel.b.bits.resp := 0.U
  io.channel.b.bits.user := DontCare

  io.channel.r.bits.last := 0.B
  io.channel.r.bits.user := DontCare
  io.channel.r.bits.resp := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.aw.ready := AWREADY
    val WREADY  = RegInit(0.B); io.channel.w .ready := WREADY
    val BVALID  = RegInit(0.B); io.channel.b .valid := BVALID
    val ARREADY = RegInit(1.B); io.channel.ar.ready := ARREADY
    val RVALID  = RegInit(0.B); io.channel.r .valid := RVALID
    val ARSIZE  = RegInit(0.U(3.W))
    val ARLEN   = RegInit(0.U(8.W))
    val AWSIZE  = RegInit(0.U(3.W))
    val AWLEN   = RegInit(0.U(8.W))

    val RID    = RegInit(0.U(idlen.W)); io.channel.r.bits.id := RID
    val BID    = RegInit(0.U(idlen.W)); io.channel.b.bits.id := BID
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
    io.channel.r.bits.data := storage_read.io.data

    val storage_write = Module(new StorageWrite)
    storage_write.io.clock := io.basic.ACLK
    storage_write.io.wen   := 0.B
    storage_write.io.addr  := AWADDR
    storage_write.io.data  := io.channel.w.bits.data
    storage_write.io.mask  := io.channel.w.bits.strb

    when(io.channel.r.fire()) {
      when(ARLEN === 0.U) {
        RVALID         := 0.B
        ARREADY        := 1.B
        io.channel.r.bits.last := 1.B
      }.otherwise {
        wireARADDR := ARADDR + wireRStep
        ARADDR     := wireARADDR
        ARLEN      := ARLEN - 1.U
      }
    }.elsewhen(io.channel.ar.fire()) {
      RID        := io.channel.ar.bits.id
      wireARADDR := io.channel.ar.bits.addr(alen - 1, axSize) ## 0.U(axSize.W) - SPIFLASH.BASE.U
      ARADDR     := wireARADDR
      ARREADY    := 0.B
      RVALID     := 1.B
      ARSIZE     := io.channel.ar.bits.size
      ARLEN      := io.channel.ar.bits.len
    }

    when(io.channel.aw.fire()) {
      AWADDR  := io.channel.aw.bits.addr(alen - 1, axSize) ## 0.U(axSize.W) - SPIFLASH.BASE.U
      BID     := io.channel.aw.bits.id
      AWREADY := 0.B
      WREADY  := 1.B
      AWSIZE  := io.channel.aw.bits.size
      AWLEN   := io.channel.aw.bits.len
    }

    when(io.channel.w.fire()) {
      storage_write.io.wen := 1.B
      when(AWLEN === 0.U) {
        WREADY  := 0.B
        BVALID  := 1.B
      }.otherwise {
        AWADDR := AWADDR + wireWStep
        AWLEN  := AWLEN - 1.U
      }
    }

    when(io.channel.b.fire()) {
      AWREADY := 1.B
      BVALID := 0.B
    }
  }
}

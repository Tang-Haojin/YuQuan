package sim.peripheral.sdcard

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import sim.SimParams

class SDCardRead(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with SimParams {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val ren   = Input (Bool())
    val addr  = Input (UInt(64.W))
    val rdata = Output(UInt(32.W))
  })

  setInline("SDCardRead.v",s"""
    |import "DPI-C" function void sdcard_read(input longint addr, output int rdata);
    |
    |module SDCardRead (
    |  input  clock,
    |  input  ren,
    |  input  [63:0] addr,
    |  output reg [31:0] rdata
    |);
    |
    |  always@(posedge clock) begin
    |    if (ren) sdcard_read(addr, rdata);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class SDCardWrite(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with SimParams {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val wen   = Input (Bool())
    val waddr = Input (UInt(8.W))
    val wdata = Input (UInt(8.W))
  })

  setInline("SDCardWrite.v", s"""
    |import "DPI-C" function void sdcard_write(input longint addr, input int data);
    |
    |module SDCardWrite (
    |  input clock,
    |  input wen,
    |  input [63:0] waddr,
    |  input [31:0] wdata
    |);
    |
    |  always@(posedge clock) begin
    |    if (wen) sdcard_write(waddr, wdata);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class SDCard(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new AxiSlaveIO)
  io.channel.b.bits.resp := 0.U
  io.channel.b.bits.user := DontCare

  io.channel.r.bits.last := 1.B
  io.channel.r.bits.user := DontCare
  io.channel.r.bits.resp := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.aw.ready := AWREADY
    val WREADY  = RegInit(0.B); io.channel.w .ready := WREADY
    val BVALID  = RegInit(0.B); io.channel.b .valid := BVALID
    val ARREADY = RegInit(1.B); io.channel.ar.ready := ARREADY
    val RVALID  = RegInit(0.B); io.channel.r .valid := RVALID

    val RID    = RegInit(0.U(idlen.W)); io.channel.r.bits.id := RID
    val BID    = RegInit(0.U(idlen.W)); io.channel.b.bits.id := BID
    val ARADDR = RegInit(0.U(8.W))
    val AWADDR = RegInit(0.U(8.W))

    val wireARADDR = WireDefault(UInt(8.W), ARADDR)

    val sdcard_read = Module(new SDCardRead)
    sdcard_read.io.clock     := io.basic.ACLK
    sdcard_read.io.ren       := 0.B
    sdcard_read.io.addr      := wireARADDR
    io.channel.r.bits.data := VecInit((0 until 8).map { i => sdcard_read.io.rdata << (8 * i) })(ARADDR(2, 0))

    val sdcard_write = Module(new SDCardWrite)
    sdcard_write.io.clock := io.basic.ACLK
    sdcard_write.io.wen   := 0.B
    sdcard_write.io.waddr := AWADDR
    sdcard_write.io.wdata := VecInit((0 until 8).map { i => io.channel.w.bits.data >> (8 * i) })(AWADDR(2, 0))

    when(io.channel.r.fire) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.ar.fire) {
      sdcard_read.io.ren := 1.B
      wireARADDR := io.channel.ar.bits.addr
      ARADDR  := wireARADDR
      RID     := io.channel.ar.bits.id
      ARREADY := 0.B
      RVALID  := 1.B
    }

    when(io.channel.aw.fire) {
      AWADDR  := io.channel.aw.bits.addr
      BID     := io.channel.aw.bits.id
      AWREADY := 0.B
      WREADY  := 1.B
    }

    when(io.channel.w.fire) {
      sdcard_write.io.wen := 1.B
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.channel.b.fire) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}

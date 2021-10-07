package sim.peripheral.uart

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._
import cpu.tools._
import sim._

class Nemu_Uart(implicit val p: Parameters) extends RawModule with SimParams {
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
    val ARADDR = RegInit(0.U(3.W))
    val AWADDR = RegInit(0.U(3.W))

    val wireARADDR = WireDefault(UInt(3.W), ARADDR)

    io.channel.r.bits.data := 0.U

    when(io.channel.r.fire()) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.ar.fire()) {
      wireARADDR := io.channel.ar.bits.addr
      ARADDR  := wireARADDR
      RID     := io.channel.ar.bits.id
      ARREADY := 0.B
      RVALID  := 1.B
    }

    when(io.channel.aw.fire()) {
      AWADDR  := io.channel.aw.bits.addr
      BID     := io.channel.aw.bits.id
      AWREADY := 0.B
      WREADY  := 1.B
    }

    when(io.channel.w.fire()) {
      printf("%c", io.channel.w.bits.data(7, 0))
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.channel.b.fire()) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}
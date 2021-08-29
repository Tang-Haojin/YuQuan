package sim.peripheral.uart

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.config.GeneralConfig._
import peripheral._
import cpu.tools._
import sim._

class Nemu_Uart(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new AxiSlaveIO)

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

    io.channel.axiRd.RDATA := 0.U

    when(io.channel.axiRd.RVALID && io.channel.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.axiRa.ARVALID && io.channel.axiRa.ARREADY) {
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
      printf("%c", io.channel.axiWd.WDATA(7, 0))
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.channel.axiWr.BVALID && io.channel.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}
package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import ExecSpecials._

class PlicIO extends AxiSlaveIO {
  val inter = Input (Vec(1024, Bool()))
  val eip   = Output(Bool())
}

class Plic extends RawModule {
  val io = IO(new PlicIO)

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

    val RID   = RegInit(0.U(4.W));    io.axiRd.RID   := RID
    val RDATA = RegInit(0.U(XLEN.W)); io.axiRd.RDATA := RDATA
    val WADDR = RegInit(0.U(XLEN.W))
    val WDATA = RegInit(0.U(XLEN.W))
    val WSTRB = RegInit(0.U((XLEN / 8).W))

    val isp = RegInit(VecInit(Seq.concat(Seq(0.U(32.W)), Seq.fill(15)(1.U(32.W)))))
    val ieb = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))
    val ipt = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))
    
    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      RID   := io.axiRa.ARID
      RDATA := 0.U
      ARREADY := 0.B
      RVALID  := 1.B
      for (i <- 0 until 16) when(io.axiRa.ARADDR === PLIC.Isp(i).U) { RDATA := isp(i) }
      for (i <- 0 until 0x80) when(io.axiRa.ARADDR === PLIC.Ipb(i).U) { RDATA := io.inter(i) }
      when(io.axiRa.ARADDR === PLIC.Ieb(10, 0).U) { RDATA := ieb(0) }
      when(io.axiRa.ARADDR === PLIC.Ipt(0).U) { RDATA := ipt(0) }
      when(io.axiRa.ARADDR === PLIC.Ic(0).U) { when(io.inter(10)) { RDATA := 10.U } }
    }
    
    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      WADDR   := io.axiWa.AWADDR
      AWREADY := 0.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      WDATA  := io.axiWd.WDATA
      WSTRB  := io.axiWd.WSTRB
      WREADY := 0.B
    }

    when(~io.axiWa.AWREADY && ~io.axiWd.WREADY && ~io.axiWr.BVALID) {
      AWREADY := 1.B
      WREADY  := 1.B
      BVALID  := 1.B
      for (i <- 1 until 16) when(WADDR === PLIC.Isp(i).U) { isp(i) := WDATA }
      when(WADDR === PLIC.Ieb(10, 0).U) { ieb(0) := WDATA }
      when(WADDR === PLIC.Ipt(0).U) { ipt(0) := WDATA }
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }

    io.eip := ((isp(10) =/= 0.U) && io.inter(10))
  }
}

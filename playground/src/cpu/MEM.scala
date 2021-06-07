package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

class MEMOutput extends Bundle {
  val rd   = Output(UInt(5.W))
  val data = Output(UInt(XLEN.W))
}

class MEM extends Module {
  val io = IO(new Bundle {
    val axiWa  = new AXIwa             // connected
    val axiWd  = new AXIwd             // connected
    val axiWr  = new AXIwr             // connected
    val axiRa  = new AXIra             // connected
    val axiRd  = new AXIrd             // connected
    val lastVR = new LastVR            // connected
    val nextVR = Flipped(new LastVR)   // connected
    val input  = Flipped(new EXOutput)
    val output = new MEMOutput
  })
  
  io.axiRa.ARID     := 1.U // 1 for MEM
  io.axiRa.ARLEN    := 0.U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.axiRa.ARSIZE   := 6.U // 2^(ARSIZE) data bit width per AXI Transfer
  io.axiRa.ARBURST  := 1.U // 1 for INCR type
  io.axiRa.ARLOCK   := 0.U // since we do not use it yet
  io.axiRa.ARCACHE  := 0.U // since we do not use it yet
  io.axiRa.ARPROT   := 0.U // since we do not use it yet
  io.axiRa.ARADDR   := io.input.addr
  io.axiRa.ARQOS    := DontCare
  io.axiRa.ARUSER   := DontCare
  io.axiRa.ARREGION := DontCare

  io.axiWa.AWID     := 1.U
  io.axiWa.AWLEN    := 0.U
  io.axiWa.AWSIZE   := 6.U
  io.axiWa.AWBURST  := 1.U
  io.axiWa.AWLOCK   := 0.U
  io.axiWa.AWCACHE  := 0.U
  io.axiWa.AWPROT   := 0.U
  io.axiWa.AWQOS    := DontCare
  io.axiWa.AWUSER   := DontCare
  io.axiWa.AWREGION := DontCare
  io.axiWa.AWADDR   := io.input.addr

  io.axiWd.WID   := 1.U
  io.axiWd.WLAST := 1.B // since we do not enable burst yet
  io.axiWd.WDATA := io.input.data
  io.axiWd.WSTRB := DontCare
  io.axiWd.WUSER := DontCare

  val NVALID  = RegInit(0.B); io.nextVR.VALID  := NVALID
  val LREADY  = RegInit(1.B); io.lastVR.READY  := LREADY

  val ARVALID = RegInit(0.B); io.axiRa.ARVALID := ARVALID
  val RREADY  = RegInit(0.B); io.axiRd.RREADY  := RREADY
  val AWVALID = RegInit(0.B); io.axiWa.AWVALID := AWVALID
  val WVALID  = RegInit(0.B); io.axiWd.WVALID  := WVALID
  val BREADY  = RegInit(0.B); io.axiWr.BREADY  := BREADY

  val rd      = RegInit(0.U(5.W));    io.output.rd   := rd
  val data    = RegInit(0.U(XLEN.W)); io.output.data := data

  // FSM
  when(io.nextVR.VALID && io.nextVR.READY) { // ready to announce the next level
    NVALID  := 0.B
    LREADY  := 1.B
  }.elsewhen(io.axiWr.BVALID && io.axiWr.BREADY) {
    when(io.axiWr.BID === 1.U) {
      BREADY := 0.B
      NVALID := 1.B
    }
  }.elsewhen((io.axiWa.AWVALID && io.axiWa.AWREADY) &&
             (io.axiWd.WVALID && io.axiWd.WREADY)) {
    AWVALID := 0.B
    WVALID  := 0.B
    BREADY  := 1.B
  }.elsewhen((io.axiWa.AWVALID && io.axiWa.AWREADY) && ~io.axiWd.WVALID) {
    AWVALID := 0.B
    BREADY  := 1.B
  }.elsewhen((io.axiWd.WVALID && io.axiWd.WREADY) && ~io.axiWa.AWVALID) {
    WVALID  := 0.B
    BREADY  := 1.B
  }.elsewhen(io.axiWa.AWVALID && io.axiWa.AWREADY) {
    AWVALID := 0.B
  }.elsewhen(io.axiWd.WVALID && io.axiWd.WREADY) {
    WVALID  := 0.B
  }.elsewhen(io.axiRd.RVALID && io.axiRd.RREADY) { // ready to receive data from BUS
    when(io.axiRd.RID === 1.U) { // remember to check the transaction ID
      RREADY := 0.B
      NVALID := 1.B
      data   := io.axiRd.RDATA
    }
  }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) { // ready to send request to BUS
    ARVALID := 0.B
    RREADY  := 1.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) {
    LREADY := 0.B
    rd     := io.input.rd
    data   := io.input.data
    when(io.input.isMem) {
      when(io.input.isLd) {
        ARVALID := 1.B
      }.otherwise {
        AWVALID := 1.B
        WVALID  := 1.B
      }
    }.otherwise {
      NVALID := 1.B
    }
  }
}

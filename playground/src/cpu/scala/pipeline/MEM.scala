package cpu

import chisel3._
import chisel3.util._
import math._

import tools._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._

class MEMOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(XLEN.W))
  val wcsr    = Output(Vec(writeCsrsPort, UInt(12.W)))
  val csrData = Output(Vec(writeCsrsPort, UInt(XLEN.W)))
  val debug   =
  if (Debug) new Bundle {
    val exit  = Output(UInt(3.W))
    val pc = Output(UInt(XLEN.W))
  } else null
}

class MEM extends Module {
  val io = IO(new Bundle {
    val axiWa  = new AXIwa
    val axiWd  = new AXIwd
    val axiWr  = new AXIwr
    val axiRa  = new AXIra
    val axiRd  = new AXIrd
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val input  = Flipped(new EXOutput)
    val output = new MEMOutput
  })

  val mask     = RegInit(0.U(8.W))
  val addr     = RegInit(0.U(ALEN.W))
  val extType  = RegInit(0.U(3.W))

  val rd      = RegInit(0.U(5.W));    io.output.rd   := rd
  val data    = RegInit(0.U(XLEN.W)); io.output.data := data
  val wcsr    = RegInit(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W)))); io.output.wcsr    := wcsr
  val csrData = RegInit(VecInit(Seq.fill(writeCsrsPort)(0.U(XLEN.W))));   io.output.csrData := csrData
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null

  val offset   = addr(AxSIZE - 1, 0)

  val wireOff = io.input.addr(AxSIZE - 1, 0)

  io.axiRa.ARID     := 1.U // 1 for MEM
  io.axiRa.ARLEN    := 0.U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.axiRa.ARSIZE   := AxSIZE.U // 2^(ARSIZE) bytes per AXI Transfer
  io.axiRa.ARBURST  := 1.U // 1 for INCR type
  io.axiRa.ARLOCK   := 0.U // since we do not use it yet
  io.axiRa.ARCACHE  := 0.U // since we do not use it yet
  io.axiRa.ARPROT   := 0.U // since we do not use it yet
  io.axiRa.ARADDR   := addr
  io.axiRa.ARQOS    := DontCare
  io.axiRa.ARUSER   := DontCare
  io.axiRa.ARREGION := DontCare

  io.axiWa.AWID     := 1.U
  io.axiWa.AWLEN    := 0.U
  io.axiWa.AWSIZE   := AxSIZE.U
  io.axiWa.AWBURST  := 1.U
  io.axiWa.AWLOCK   := 0.U
  io.axiWa.AWCACHE  := 0.U
  io.axiWa.AWPROT   := 0.U
  io.axiWa.AWQOS    := DontCare
  io.axiWa.AWUSER   := DontCare
  io.axiWa.AWREGION := DontCare
  io.axiWa.AWADDR   := addr

  io.axiWd.WLAST := 1.B // since we do not enable burst yet
  io.axiWd.WDATA := data
  io.axiWd.WSTRB := mask
  io.axiWd.WUSER := DontCare

  val NVALID  = RegInit(0.B); io.nextVR.VALID  := NVALID
  val LREADY  = RegInit(1.B); io.lastVR.READY  := LREADY
  val isFree  = RegInit(1.B)

  val ARVALID = RegInit(0.B); io.axiRa.ARVALID := ARVALID
  val RREADY  = RegInit(0.B); io.axiRd.RREADY  := RREADY
  val AWVALID = RegInit(0.B); io.axiWa.AWVALID := AWVALID
  val WVALID  = RegInit(0.B); io.axiWd.WVALID  := WVALID
  val BREADY  = RegInit(0.B); io.axiWr.BREADY  := BREADY

  val shiftRdata = VecInit((0 until 8).map { i => io.axiRd.RDATA >> (8 * i) })(offset)
  val extRdata   = VecInit((0 until 7).map { i => i match {
    case 0 => Fill(XLEN - 8 , shiftRdata(7 )) ## shiftRdata(7 , 0)
    case 1 => Fill(XLEN - 16, shiftRdata(15)) ## shiftRdata(15, 0)
    case 2 => Fill(XLEN - 32, shiftRdata(31)) ## shiftRdata(31, 0)
    case 4 => shiftRdata(7 , 0)
    case 5 => shiftRdata(15, 0)
    case 6 => shiftRdata(31, 0)
    case 3 if (XLEN >= 64) => shiftRdata(63, 0)
    case _ => 0.U(XLEN.W)
  }})(extType)

  val rawStrb = VecInit((0 until 4).map { i => Fill(pow(2, i).round.toInt, 1.B) })(io.input.mask)

  io.lastVR.READY := isFree && io.nextVR.READY

  when(io.axiWr.BVALID && io.axiWr.BREADY) {
    when(io.axiWr.BID === 1.U) {
      BREADY := 0.B
      NVALID := 1.B
      isFree := 1.B
    }
  }.elsewhen((io.axiWa.AWVALID && io.axiWa.AWREADY) &&
             (io.axiWd.WVALID && io.axiWd.WREADY)) {
    AWVALID := 0.B
    WVALID  := 0.B
    BREADY  := 1.B
    // printf("io.axiWd.WDATA: %x\n", io.axiWd.WDATA)
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
      isFree := 1.B
      data   := extRdata
    }
  }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) { // ready to send request to BUS
    ARVALID := 0.B
    RREADY  := 1.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) {
    LREADY  := 0.B
    rd      := io.input.rd
    addr    := io.input.addr
    wcsr    := io.input.wcsr
    csrData := io.input.csrData
    data    := io.input.data
    mask    := VecInit((0 until 8).map { i => rawStrb << i })(wireOff)
    extType := io.input.mask
    if (Debug) {
      exit := io.input.debug.exit
      pc   := io.input.debug.pc
    }
    when(io.input.isMem) {
      NVALID := 0.B
      isFree := 0.B
      when(io.input.isLd) {
        ARVALID := 1.B
      }.otherwise {
        AWVALID := 1.B
        WVALID  := 1.B
        data    := VecInit((0 until 8).map { i => io.input.data << (8 * i) })(wireOff)
      }
    }.otherwise {
      NVALID := 1.B
      isFree := 1.B
    }
  }.otherwise {
    NVALID := 0.B
  }

  if (debugIO) {
    printf("mem_last_ready = %d\n", io.lastVR.READY )
    printf("mem_last_valid = %d\n", io.lastVR.VALID )
    printf("mem_next_ready = %d\n", io.nextVR.READY )
    printf("mem_next_valid = %d\n", io.nextVR.VALID )
    printf("io.output.rd   = %d\n", io.output.rd    )
    printf("io.output.data = %d\n", io.output.data  )
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
  }
}

package cpu.pipeline

import math._
import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

import cpu.cache._
import cpu.tools._

class MEM(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val dcache = Flipped(new CpuIO)
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val input  = Flipped(new EXOutput)
    val output = new MEMOutput
  })

  val mask     = RegInit(0.U(8.W))
  val addr     = RegInit(0.U(xlen.W))
  val extType  = RegInit(0.U(3.W))

  val rd      = RegInit(0.U(5.W));    io.output.rd   := rd
  val data    = RegInit(0.U(xlen.W)); io.output.data := data
  val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W)))); io.output.wcsr    := wcsr
  val csrData = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))));   io.output.csrData := csrData
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val pc      = if (Debug) RegInit(0.U(xlen.W)) else null

  val offset   = addr(axSize - 1, 0)

  val wireOff = io.input.addr(axSize - 1, 0)

  val NVALID  = RegInit(0.B); io.nextVR.VALID := NVALID
  val LREADY  = RegInit(1.B); io.lastVR.READY := LREADY && io.nextVR.READY

  val isMem = RegInit(0.B); val wireIsMem = WireDefault(Bool(), isMem)
  val rw    = RegInit(0.B); val wireRw    = WireDefault(Bool(), rw)

  val wireData = WireDefault(UInt(xlen.W), data)
  val wireAddr = WireDefault(UInt(xlen.W), addr)
  val wireMask = WireDefault(UInt((xlen / 8).W), mask)

  val shiftRdata = VecInit((0 until 8).map { i => io.dcache.cpuResult.data >> (8 * i) })(offset)
  val extRdata   = VecInit((0 until 7).map {
    case 0 => Fill(xlen - 8 , shiftRdata(7 )) ## shiftRdata(7 , 0)
    case 1 => Fill(xlen - 16, shiftRdata(15)) ## shiftRdata(15, 0)
    case 2 => Fill(xlen - 32, shiftRdata(31)) ## shiftRdata(31, 0)
    case 4 => shiftRdata(7 , 0)
    case 5 => shiftRdata(15, 0)
    case 6 => shiftRdata(31, 0)
    case 3 if xlen >= 64 => shiftRdata(63, 0)
    case _ => 0.U(xlen.W)
  })(extType)

  val rawStrb = VecInit((0 until 4).map { i => Fill(pow(2, i).round.toInt, 1.B) })(io.input.mask)

  io.dcache.cpuReq.data  := wireData
  io.dcache.cpuReq.rw    := wireRw
  io.dcache.cpuReq.wmask := wireMask
  io.dcache.cpuReq.valid := wireIsMem
  io.dcache.cpuReq.addr  := wireAddr

  when(io.dcache.cpuResult.ready) {
    LREADY := 1.B
    NVALID := 1.B
    isMem  := 0.B
    rw     := 0.B
    when(!rw) { data := extRdata }
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) {
    rd       := io.input.rd
    wireAddr := io.input.addr
    wireData := io.input.data
    wireMask := VecInit((0 until 8).map { i => rawStrb << i })(wireOff)
    addr     := wireAddr
    data     := wireData
    mask     := wireMask
    wcsr     := io.input.wcsr
    csrData  := io.input.csrData
    extType  := io.input.mask
    if (Debug) {
      exit := io.input.debug.exit
      pc   := io.input.debug.pc
    }
    when(io.input.isMem) {
      NVALID    := 0.B
      LREADY    := 0.B
      wireIsMem := 1.B
      isMem     := 1.B
      rw        := wireRw
      wireData  := VecInit((0 until 8).map { i => io.input.data << (8 * i) })(wireOff)
      when(io.input.isLd) { wireRw := 0.B }
      .otherwise          { wireRw := 1.B }
    }.otherwise {
      NVALID := 1.B
      LREADY := 1.B
    }
  }.otherwise {
    NVALID := 0.B
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
  }
}

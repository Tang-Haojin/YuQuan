package cpu.pipeline

import chisel3._
import chisel3.util._
import math._

import tools._

import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import cpu.cache._

class MEM extends Module {
  val io = IO(new Bundle {
    val dcache = Flipped(new CpuIO)
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val input  = Flipped(new EXOutput)
    val output = new MEMOutput
  })

  val mask     = RegInit(0.U(8.W))
  val addr     = RegInit(0.U(XLEN.W))
  val extType  = RegInit(0.U(3.W))

  val rd      = RegInit(0.U(5.W));    io.output.rd   := rd
  val data    = RegInit(0.U(XLEN.W)); io.output.data := data
  val wcsr    = RegInit(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W)))); io.output.wcsr    := wcsr
  val csrData = RegInit(VecInit(Seq.fill(writeCsrsPort)(0.U(XLEN.W))));   io.output.csrData := csrData
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null

  val offset   = addr(AxSIZE - 1, 0)

  val wireOff = io.input.addr(AxSIZE - 1, 0)

  val NVALID  = RegInit(0.B); io.nextVR.VALID := NVALID
  val LREADY  = RegInit(1.B); io.lastVR.READY := LREADY && io.nextVR.READY

  val isMem = RegInit(0.B); val wireIsMem = WireDefault(Bool(), isMem)
  val rw    = RegInit(0.B); val wireRw    = WireDefault(Bool(), rw)

  val wireData = WireDefault(UInt(XLEN.W), data)
  val wireAddr = WireDefault(UInt(XLEN.W), addr)
  val wireMask = WireDefault(UInt((XLEN / 8).W), mask)

  val shiftRdata = VecInit((0 until 8).map { i => io.dcache.cpuResult.data >> (8 * i) })(offset)
  val extRdata   = VecInit((0 until 7).map {
    case 0 => Fill(XLEN - 8 , shiftRdata(7 )) ## shiftRdata(7 , 0)
    case 1 => Fill(XLEN - 16, shiftRdata(15)) ## shiftRdata(15, 0)
    case 2 => Fill(XLEN - 32, shiftRdata(31)) ## shiftRdata(31, 0)
    case 4 => shiftRdata(7 , 0)
    case 5 => shiftRdata(15, 0)
    case 6 => shiftRdata(31, 0)
    case 3 if XLEN >= 64 => shiftRdata(63, 0)
    case _ => 0.U(XLEN.W)
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

  if (debugIO) {
    printf("mem_last_ready = %d\n", io.lastVR.READY)
    printf("mem_last_valid = %d\n", io.lastVR.VALID)
    printf("mem_next_ready = %d\n", io.nextVR.READY)
    printf("mem_next_valid = %d\n", io.nextVR.VALID)
    printf("io.output.rd   = %d\n", io.output.rd   )
    printf("io.output.data = %d\n", io.output.data )
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
  }
}

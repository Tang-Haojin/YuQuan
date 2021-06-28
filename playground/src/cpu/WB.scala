package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import cpu.privileged.CSRsW

class WB extends Module {
  val io = IO(new Bundle {
    val gprsW  = Flipped(new GPRsW)
    val csrsW  = Flipped(new CSRsW)
    val lastVR = new LastVR
    val input  = Flipped(new MEMOutput)
    val debug = if (Debug) new Bundle {
      val showReg = if (cpu.config.Debug.showReg) Output(Bool()) else null
      val pc      = Output(UInt(XLEN.W))
      val exit    = Output(UInt(3.W))
      val wbvalid = Output(Bool())
      val rd      = Output(UInt(5.W))
    } else null
  })

  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val wbvalid = if (Debug) RegInit(0.B) else null
  val rd      = if (Debug) RegInit(0.U(5.W)) else null

  io.gprsW.wen   := 0.B
  io.gprsW.waddr := io.input.rd
  io.gprsW.wdata := io.input.data

  io.csrsW.wen   := 0.B
  io.csrsW.wcsr  := io.input.wcsr
  io.csrsW.wdata := io.input.csrData

  io.lastVR.READY := 1.B

  val regShowReg = if (showReg) RegInit(0.B) else null
  if (showReg) {
    io.debug.showReg := regShowReg
    regShowReg := 0.B
  }
  
  when(io.lastVR.VALID) { // ready to start fetching instr
    io.gprsW.wen := (io.input.rd =/= 0.U)
    for (i <- 0 until writeCsrsPort) io.csrsW.wen(i) := (io.input.wcsr(i) =/= 0xFFF.U)
    if (showReg) regShowReg := 1.B
    if (Debug) {
      exit  := io.input.debug.exit
      pc    := io.input.debug.pc
      rd    := io.input.rd
    }
  }

  if (debugIO) {
    printf("wb_last_ready    = %d\n", io.lastVR.READY )
    printf("wb_last_valid    = %d\n", io.lastVR.VALID )
    printf("io.gprsW.wen     = %d\n", io.gprsW.wen    )
  }

  if (Debug) {
    io.debug.exit    := exit
    io.debug.pc      := pc
    io.debug.wbvalid := wbvalid
    io.debug.rd      := rd
    wbvalid := io.lastVR.VALID
  }
}

package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._

class WB extends Module {
  val io = IO(new Bundle {
    val gprsW  = Flipped(new GPRsW)
    val lastVR = new LastVR
    val input  = Flipped(new MEMOutput)
    val debug = if (showReg) new Bundle {
      val showReg = Output(Bool())
    } else null
  })

  io.gprsW.wen   := 0.B
  io.gprsW.waddr := io.input.rd
  io.gprsW.wdata := io.input.data

  io.lastVR.READY := 1.B

  val regShowReg = if (showReg) RegInit(0.B) else null
  if (showReg) {
    io.debug.showReg := regShowReg
    regShowReg := 0.B
  }
  
  when(io.lastVR.VALID) { // ready to start fetching instr
    io.gprsW.wen := (io.input.rd =/= 0.U)
    if (showReg) regShowReg := 1.B
  }

  if (debugIO && false) {
    printf("wb_last_ready    = %d\n", io.lastVR.READY )
    printf("wb_last_valid    = %d\n", io.lastVR.VALID )
    printf("io.gprsW.wen     = %d\n", io.gprsW.wen    )
  }
}

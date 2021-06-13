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
    val nextVR = Flipped(new LastVR)
    val input  = Flipped(new MEMOutput)
  })

  io.gprsW.wen   := 0.B
  io.gprsW.waddr := io.input.rd
  io.gprsW.wdata := io.input.data

  val NVALID  = RegInit(1.B); io.nextVR.VALID := NVALID
  val LREADY  = RegInit(0.B); io.lastVR.READY := LREADY

  io.lastVR.READY := 1.B
  // FSM
  when(io.nextVR.VALID && io.nextVR.READY) { // ready to announce the next level
    NVALID  := 0.B
    LREADY  := 1.B
    io.gprsW.wen := 0.B
  }
  
  when(io.lastVR.VALID && io.lastVR.READY) { // ready to start fetching instr
    LREADY  := 0.B
    NVALID  := 1.B
    io.gprsW.wen := (io.input.rd =/= 0.U)
  }

  if (debugIO && false) {
    printf("wb_last_ready    = %d\n", io.lastVR.READY )
    printf("wb_last_valid    = %d\n", io.lastVR.VALID )
    printf("wb_next_ready    = %d\n", io.nextVR.READY )
    printf("wb_next_valid    = %d\n", io.nextVR.VALID )
    printf("io.gprsW.wen     = %d\n", io.gprsW.wen    )
  }
}

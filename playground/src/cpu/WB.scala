package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

class WB extends Module {
  val io = IO(new Bundle {
    val gprsW  = Flipped(new GPRsW)  // connected
    val lastVR = new LastVR          // connected
    val nextVR = Flipped(new LastVR) // connected
    val input  = Flipped(new MEMOutput)
  })

  io.gprsW.wen   := 0.B
  io.gprsW.waddr := io.input.rd
  io.gprsW.wdata := io.input.data

  val NVALID  = RegInit(0.B); io.nextVR.VALID := NVALID
  val LREADY  = RegInit(1.B); io.lastVR.READY := LREADY

  // FSM with a little simple combinational logic
  when(io.nextVR.VALID && io.nextVR.READY) { // ready to announce the next level
    NVALID  := 0.B
    LREADY  := 1.B
    io.gprsW.wen := 0.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) { // ready to start fetching instr
    LREADY  := 0.B
    NVALID  := 1.B
    io.gprsW.wen := (io.input.rd === 0.U)
  }.otherwise {
    io.gprsW.wen := 0.B
  }
}

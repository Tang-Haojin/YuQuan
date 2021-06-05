package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

import meta.Booleans._
import meta.PreProc._

class WB extends RawModule {
  val io = IO(new Bundle {
    val basic  = new BASIC           // connected
    val gprsW  = Flipped(new GPRsW)  // connected
    val lastVR = new LastVR          // connected
    val nextVR = Flipped(new LastVR) // connected
    val input  = Flipped(new MEMOutput)
    // ???
  })

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    
  }
}

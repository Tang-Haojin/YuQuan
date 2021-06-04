package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

import meta.Booleans._
import meta.PreProc._

class WB extends Module {
  val io = IO(new Bundle {
    val wbBasic = new BASIC              // connected
    val wbGprsW = Flipped(new GPRsW)     // connected
    val wbLastVR   = new LastVR          // connected
    val wbNextVR   = Flipped(new LastVR) // connected
    // ???
  })
}

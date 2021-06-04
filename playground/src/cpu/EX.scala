package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

import meta.Booleans._
import meta.PreProc._

class EX extends RawModule {
  val io = IO(new Bundle {
    val exBasic    = new BASIC             // connected
    val exData     = Flipped(new IDOutput) // connected
    val exLastVR   = new LastVR            // connected
    val exNextVR   = Flipped(new LastVR)   // connected
    // ???
  })
}

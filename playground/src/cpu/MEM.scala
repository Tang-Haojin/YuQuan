package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

import meta.Booleans._
import meta.PreProc._

class MEM extends RawModule {
  val io = IO(new Bundle {
    val memBasic    = new BASIC           // connected
    val memAxiWa    = new AXIwa           // connected
    val memAxiWd    = new AXIwd           // connected
    val memAxiWr    = new AXIwr           // connected
    val memAxiRa    = new AXIra           // connected
    val memAxiRd    = new AXIrd           // connected
    val memLastVR   = new LastVR          // connected
    val memNextVR   = Flipped(new LastVR) // connected
    val memWen      = Input(Bool())
    val memMask     = Input(UInt(XLEN.W))
  })
}
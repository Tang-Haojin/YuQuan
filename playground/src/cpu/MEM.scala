package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

import meta.Booleans._
import meta.PreProc._

class MEMOutput extends Bundle {
  val rd   = Output(UInt(5.W))
  val data = Output(UInt(XLEN.W))
}

class MEM extends RawModule {
  val io = IO(new Bundle {
    val basic  = new BASIC             // connected
    val axiWa  = new AXIwa             // connected
    val axiWd  = new AXIwd             // connected
    val axiWr  = new AXIwr             // connected
    val axiRa  = new AXIra             // connected
    val axiRd  = new AXIrd             // connected
    val lastVR = new LastVR            // connected
    val nextVR = Flipped(new LastVR)   // connected
    val input  = Flipped(new IDOutput)
    val output = new MEMOutput
  })
}
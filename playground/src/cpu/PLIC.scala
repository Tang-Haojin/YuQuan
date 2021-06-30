package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import ExecSpecials._

class IsPlic extends Module {
  val io = IO(new Bundle {
    val addr_in  = Input(UInt(XLEN.W))
    val addr_out = Output(UInt(12.W))
  })

  class CSRsAddr extends cpu.privileged.CSRsAddr
  val csrsAddr = new CSRsAddr

}
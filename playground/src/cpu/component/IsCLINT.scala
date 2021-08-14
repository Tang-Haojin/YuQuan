package cpu.component

import chisel3._
import chisel3.util._

import cpu.config.GeneralConfig._

class IsCLINT extends Module {
  val io = IO(new Bundle {
    val addr_in  = Input(UInt(XLEN.W))
    val addr_out = Output(UInt(12.W))
  })

  class CSRsAddr extends cpu.privileged.CSRsAddr
  val csrsAddr = new CSRsAddr

  io.addr_out := 0xFFF.U

  switch(io.addr_in) {
    is(CLINT.MTIME.U) { io.addr_out := csrsAddr.Mtime }
    is(CLINT.MTIMECMP(0).U) { io.addr_out := csrsAddr.Mtimecmp }
  }

}

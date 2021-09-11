package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu._

class IsCLINT(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val addr_in  = Input(UInt(xlen.W))
    val addr_out = Output(UInt(12.W))
  })
  private case class CSRsAddr()(implicit val p: Parameters) extends CPUParams with cpu.privileged.CSRsAddr
  io.addr_out := 0xFFF.U
  switch(io.addr_in) {
    is(CLINT.MTIME.U) { io.addr_out := CSRsAddr().Mtime }
    is(CLINT.MTIMECMP(0).U) { io.addr_out := CSRsAddr().Mtimecmp }
  }
}

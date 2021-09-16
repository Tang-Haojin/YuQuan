package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class BypassCsr(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = Input (Bool())
    val idOut   = Input (Bool())
    val exOut   = Input (Bool())
    val memOut  = Input (Bool())
    val isWait  = Output(Bool())
  })

  io.isWait := Mux(io.receive || (io.memOut && io.exOut && io.idOut), 0.B, 1.B)
}

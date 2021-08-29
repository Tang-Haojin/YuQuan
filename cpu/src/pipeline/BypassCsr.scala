package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.config.RegisterConfig._
import cpu.tools._

class BypassCsr(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = new cpu.privileged.CSRsR
    val request = Flipped(new cpu.privileged.CSRsR)
    val idOut   = new CsrVal
    val exOut   = new CsrVal
    val memOut  = new CsrVal
    val isWait  = Output(Bool())
  })

  io.isWait := 0.B

  for (i <- 0 until readCsrsPort) {
    io.request.rcsr(i) := io.receive.rcsr(i)
    io.receive.rdata(i) := 0.U
    when(io.receive.rcsr(i) =/= 0xFFF.U) {
      io.receive.rdata(i) := io.request.rdata(i)
      for (j <- 0 until writeCsrsPort)
        when(io.receive.rcsr(i) === io.memOut.wcsr(j)) {
          io.receive.rdata(i) := io.memOut.value(j)
        }
      for (j <- 0 until writeCsrsPort)
        when(io.receive.rcsr(i) === io.exOut.wcsr(j)) {
          io.receive.rdata(i) := io.exOut.value(j)
        }
      for (j <- 0 until writeCsrsPort)
        when(io.receive.rcsr(i) === io.idOut.wcsr(j)) {
          io.isWait := 1.B
          io.receive.rdata(i) := 0.U
        }
    }
  }
}

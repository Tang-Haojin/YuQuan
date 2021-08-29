package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.config.RegisterConfig._
import cpu.tools._

class Bypass(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = new cpu.component.GPRsR
    val request = Flipped(new cpu.component.GPRsR)
    val idOut  = new RdVal
    val exOut  = new RdVal
    val memOut = new RdVal
    val isLd   = Input (Bool())
    val isWait = Output(Bool())
  })

  io.isWait := 0.B

  for (i <- 0 until readPortsNum) {
    io.request.raddr(i) := io.receive.raddr(i)
    io.receive.rdata(i) := 0.U
    when(io.receive.raddr(i) === 0.U) {
      io.receive.rdata(i) := 0.U
    }.otherwise {
      when(io.receive.raddr(i) === io.idOut.index) {
        io.isWait := 1.B
      }.elsewhen(io.receive.raddr(i) === io.exOut.index) {
        when(io.isLd) {
          io.isWait := 1.B
        }.otherwise {
          io.receive.rdata(i) := io.exOut.value
        }
      }.elsewhen(io.receive.raddr(i) === io.memOut.index) {
        io.receive.rdata(i) := io.memOut.value
      }.otherwise {
        io.receive.rdata(i) := io.request.rdata(i)
      }
    }
  }
}

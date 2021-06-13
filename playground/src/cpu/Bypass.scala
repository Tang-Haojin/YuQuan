package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import cpu.ExecSpecials._
import cpu.InstrTypes._

class RdVal extends Bundle {
  val index = Input(UInt(5.W))
  val value = Input(UInt(XLEN.W))
}

class Bypass extends Module {
  val io = IO(new Bundle {
    val receive = new cpu.register.GPRsR
    val request = Flipped(new cpu.register.GPRsR)
    val idOut  = new RdVal
    val exOut  = new RdVal
    val memOut = new RdVal
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
        io.receive.rdata(i) := io.exOut.value
      }.elsewhen(io.receive.raddr(i) === io.memOut.index) {
        io.receive.rdata(i) := io.memOut.value
      }.otherwise {
        io.receive.rdata(i) := io.request.rdata(i)
      }
    }
  }
}

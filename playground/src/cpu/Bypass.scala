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

class CsrVal extends Bundle {
  val wcsr  = Input(UInt(12.W))
  val value = Input(UInt(XLEN.W))
}

class Bypass_csr extends Module {
  val io = IO(new Bundle {
    val receive = new cpu.privileged.CSRsR
    val request = Flipped(new cpu.privileged.CSRsR)
    val idOut   = new CsrVal
    val exOut   = new CsrVal
    val memOut  = new CsrVal
    val isWait  = Output(Bool())
  })

  io.isWait := 0.B

  io.request.rcsr := io.receive.rcsr
  io.receive.rdata := 0.U
  when(io.receive.rcsr =/= 0xFFF.U) {
    when(io.receive.rcsr === io.idOut.wcsr) {
      io.isWait := 1.B
    }.elsewhen(io.receive.rcsr === io.exOut.wcsr) {
      io.receive.rdata := io.exOut.value
    }.elsewhen(io.receive.rcsr === io.memOut.wcsr) {
      io.receive.rdata := io.memOut.value
    }.otherwise {
      io.receive.rdata := io.request.rdata
    }
  }
}

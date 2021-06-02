package cpu.register

import chisel3._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

object GPRs extends Module {
  val io = IO(new Bundle {
    val wen   = Input (Bool())
    val waddr = Input (UInt(5.W))
    val wdata = Input (UInt(XLEN.W))
    val raddr = Input (Vec(readPortsNum, UInt( 5.W)))
    val rdata = Output(Vec(readPortsNum, UInt(XLEN.W)))
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(XLEN.W))))

  when(io.wen && io.waddr =/= 0.U) {
    regs(io.waddr) := io.wdata
  }

  for (i <- 0 until readPortsNum) {
    io.rdata(i) := regs(io.raddr(i))
  }
}

object PC extends Module {
  val io = IO(new Bundle {
    val wen   = Input (Bool())
    val wdata = Input (UInt(XLEN.W))
    val rdata = Output(UInt(XLEN.W))
  })

  val reg = RegInit(0.U(XLEN.W))

  when(io.wen) {
    reg := io.wdata
  }

  io.rdata := reg
}

package cpu.register

import chisel3._
import cpu.axi._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

class GPRsW extends Bundle {
  val wen   = Input (Bool())
  val waddr = Input (UInt(5.W))
  val wdata = Input (UInt(XLEN.W))
}

class GPRsR extends Bundle {
  val raddr = Input (Vec(readPortsNum, UInt( 5.W)))
  val rdata = Output(Vec(readPortsNum, UInt(XLEN.W)))
}

class GPRs extends RawModule {
  val io = IO(new Bundle {
    val gprsBasic = new BASIC
    val gprsW     = new GPRsW
    val gprsR     = new GPRsR
  })

  withClockAndReset(io.gprsBasic.ACLK, ~io.gprsBasic.ARESETn) {
    val regs = RegInit(VecInit(Seq.fill(32)(0.U(XLEN.W))))

    when(io.gprsW.wen && io.gprsW.waddr =/= 0.U) {
      regs(io.gprsW.waddr) := io.gprsW.wdata
    }

    for (i <- 0 until readPortsNum) {
      io.gprsR.rdata(i) := regs(io.gprsR.raddr(i))
    }
  }
}

class PCIO extends Bundle {
  val wen   = Input (Bool())
  val wdata = Input (UInt(XLEN.W))
  val rdata = Output(UInt(XLEN.W))
}

class PC extends Module {
  val io = IO(new PCIO)

  val reg = RegInit(0.U(XLEN.W))

  when(io.wen) {
    reg := io.wdata
  }

  io.rdata := reg
}

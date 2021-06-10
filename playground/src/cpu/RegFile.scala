package cpu.register

import chisel3._
import cpu.axi._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._

class GPRsW extends Bundle {
  val wen   = Input (Bool())
  val waddr = Input (UInt(5.W))
  val wdata = Input (UInt(XLEN.W))
}

class GPRsR extends Bundle {
  val raddr = Input (Vec(readPortsNum, UInt( 5.W)))
  val rdata = Output(Vec(readPortsNum, UInt(XLEN.W)))
}

class GPRs extends Module {
  val io = IO(new Bundle {
    val gprsW = new GPRsW
    val gprsR = new GPRsR
    val debug = if (showReg) new Bundle {
      val showReg = Input(Bool())
    } else null
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(XLEN.W))))

  when(io.gprsW.wen && io.gprsW.waddr =/= 0.U) {
    regs(io.gprsW.waddr) := io.gprsW.wdata
  }

  for (i <- 0 until readPortsNum) {
    io.gprsR.rdata(i) := regs(io.gprsR.raddr(i))
  }

  if (showReg) {
    when(io.debug.showReg) {
      for (i <- 0 until 32) {
        if (!partialReg || (partialReg && showRegList(i))) {
          printf("\t%c%c%c\t%x\n", regNames.regNames(i)(0).U, regNames.regNames(i)(1).U, regNames.regNames(i)(2).U, regs(i.U))
        }
      }
    }
  }
}

class PCIO extends Bundle {
  val wen   = Input (Bool())
  val wdata = Input (UInt(XLEN.W))
  val rdata = Output(UInt(XLEN.W))
}

class PC extends Module {
  val io = IO(new Bundle {
    val pcIo  = new PCIO
    val debug = if (showReg) new Bundle {
      val showReg = Input(Bool())
    } else null
  })

  val reg = RegInit(MEMBase.U(XLEN.W))
  when(io.pcIo.wen) {
    reg := io.pcIo.wdata
  }
  io.pcIo.rdata := reg

  if (showReg) {
    when(io.debug.showReg) {
      printf("\n\tPC\t%x\n", reg)
    }
  }
}

object regNames {
  val regNames = List(
    "$0 ", "ra ", "sp ", "gp ", "tp ", "t0 ", "t1 ", "t2 ",
    "s0 ", "s1 ", "a0 ", "a1 ", "a2 ", "a3 ", "a4 ", "a5 ",
    "a6 ", "a7 ", "s2 ", "s3 ", "s4 ", "s5 ", "s6 ", "s7 ",
    "s8 ", "s9 ", "s10", "s11", "t3 ", "t4 ", "t5 ", "t6 "
  )
}
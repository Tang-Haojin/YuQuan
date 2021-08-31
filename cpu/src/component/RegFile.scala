package cpu.component

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class GPRsW(implicit p: Parameters) extends YQBundle {
  val wen   = Input (Bool())
  val waddr = Input (UInt(5.W))
  val wdata = Input (UInt(xlen.W))
}

class GPRsR(implicit p: Parameters) extends YQBundle {
  val raddr = Input (Vec(RegConf.readPortsNum, UInt( 5.W)))
  val rdata = Output(Vec(RegConf.readPortsNum, UInt(xlen.W)))
}

class GPRs(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val gprsW = new GPRsW
    val gprsR = new GPRsR
    val debug = if (Debug) new YQBundle {
      val gprs    = Output(Vec(32, UInt(xlen.W)))
    } else null
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(xlen.W))))

  when(io.gprsW.wen && io.gprsW.waddr =/= 0.U) {
    regs(io.gprsW.waddr) := io.gprsW.wdata
  }

  for (i <- 0 until RegConf.readPortsNum)
    io.gprsR.rdata(i) := regs(io.gprsR.raddr(i))

  if (Debug) {
    io.debug.gprs := regs
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

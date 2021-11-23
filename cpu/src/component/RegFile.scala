package cpu.component

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class GPRsW(implicit p: Parameters) extends YQBundle {
  val wen    = Input(Bool())
  val waddr  = Input(UInt(5.W))
  val wdata  = Input(UInt(xlen.W))
  val retire = Input(Bool())
  val except = Input(Bool())
}

class GPRsR(implicit p: Parameters) extends YQBundle {
  val raddr = Input (Vec(RegConf.readPortsNum, UInt( 5.W)))
  val rdata = Output(Vec(RegConf.readPortsNum, UInt(xlen.W)))
}

class GPRs(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val gprsW = new GPRsW
    val rregs = Output(Vec(32, UInt(xlen.W)))
    val debug = if (Debug) new YQBundle {
      val gprs    = Output(Vec(32, UInt(xlen.W)))
    } else null
  })
  private val regs  = RegInit(VecInit(Seq.fill(32)(0.U(xlen.W))))
  private val rregs = WireDefault(Vec(32, UInt(xlen.W)), regs); rregs(0) := 0.U; io.rregs := rregs
  private val rd    = RegInit(0.U(5.W))
  when(io.gprsW.wen) {
    when(io.gprsW.waddr =/= 0.U) { regs(io.gprsW.waddr) := io.gprsW.wdata }
    when(!io.gprsW.retire) { rd := io.gprsW.waddr; regs(0) := regs(io.gprsW.waddr) }
    .otherwise {
      when(io.gprsW.except && rd =/= 0.U) { regs(rd) := regs(0) }
      rd := 0.U
    }
  }
  if (Debug) io.debug.gprs := rregs
}

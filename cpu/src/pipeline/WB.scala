package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import utils._

import cpu.component._
import cpu.privileged.CSRsW
import cpu.tools._

class WB(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val gprsW  = Flipped(new GPRsW)
    val csrsW  = Flipped(new CSRsW)
    val lastVR = new LastVR
    val input  = Flipped(new MEMOutput)
    val retire = Output(Bool())
    val debug = if (Debug) new YQBundle {
      val pc    = Output(UInt(alen.W))
      val exit  = Output(UInt(3.W))
      val rd    = Output(UInt(5.W))
      val rcsr  = Output(UInt(12.W))
      val mmio  = Output(Bool())
      val clint = Output(Bool())
      val intr  = Output(Bool())
      val priv  = Output(UInt(2.W))
    } else null
  })

  private val pc    = if (Debug) RegInit(0.U(alen.W)) else null
  private val exit  = if (Debug) RegInit(0.U(3.W)) else null
  private val rd    = if (Debug) RegInit(0.U(5.W)) else null
  private val rcsr  = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val mmio  = if (Debug) RegInit(0.B) else null
  private val clint = if (Debug) RegInit(0.B) else null
  private val intr  = if (Debug) RegInit(0.B) else null
  private val priv  = if (Debug) RegInit("b11".U(2.W)) else null

  io.gprsW.wen   := 0.B
  io.gprsW.waddr := io.input.rd
  io.gprsW.wdata := io.input.data

  io.csrsW.wen   := VecInit(Seq.fill(RegConf.writeCsrsPort)(0.B))
  io.csrsW.wcsr  := io.input.wcsr
  io.csrsW.wdata := io.input.csrData

  io.lastVR.READY := 1.B
  io.retire       := RegNext(io.lastVR.VALID && io.input.retire)
  
  when(io.lastVR.VALID) { // ready to start fetching instr
    io.gprsW.wen := io.input.rd =/= 0.U
    for (i <- io.csrsW.wen.indices) io.csrsW.wen(i) := (io.input.wcsr(i) =/= 0xFFF.U)
    if (Debug) {
      exit  := io.input.debug.exit
      pc    := io.input.debug.pc
      rd    := io.input.rd
      rcsr  := io.input.debug.rcsr
      mmio  := io.input.debug.mmio
      clint := io.input.debug.clint
      intr  := io.input.debug.intr
      priv  := io.input.debug.priv
    }
  }

  if (Debug) {
    io.debug.exit  := exit
    io.debug.pc    := pc
    io.debug.rd    := rd
    io.debug.rcsr  := rcsr
    io.debug.mmio  := mmio
    io.debug.clint := clint
    io.debug.intr  := intr
    io.debug.priv  := priv
  }
}

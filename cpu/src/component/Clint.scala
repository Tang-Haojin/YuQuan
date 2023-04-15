package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import utils._

class ClintIO extends SimpleRWIO(2, 64)

class Clint(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val clintIO = new ClintIO
    val mtime   = Output(UInt(64.W))
    val mtip    = Output(Bool())
    val msip    = Output(Bool())
  })

  private val msip     = RegInit(0.B)
  private val mtime    = RegInit(0.U(64.W))
  private val mtimecmp = RegInit(0.U(64.W))
  io.mtime := mtime
  io.mtip  := mtime > mtimecmp
  io.msip  := msip
  mtime    := mtime + 1.U

  when(io.clintIO.wen) {
    when(io.clintIO.addr === 0.U) { mtime := io.clintIO.wdata }
    when(io.clintIO.addr === 1.U) { mtimecmp := io.clintIO.wdata }
    when(io.clintIO.addr === 2.U) { msip := io.clintIO.wdata } // TODO: if there are more than one hart, it can be `wdata(32)`
  }

  io.clintIO.rdata := MuxLookup(io.clintIO.addr, mtime)(Seq(1.U -> mtimecmp, 2.U -> msip))
}

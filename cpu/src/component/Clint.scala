package cpu.component

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class ClintIO extends Bundle {
  val wen   = Input (Bool())
  val addr  = Input (UInt(1.W))
  val rdata = Output(UInt(64.W)) // 64 bits are cross-XLEN
  val wdata = Input (UInt(64.W))
}

class Clint(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val clintIO = new ClintIO
    val mtime   = Output(UInt(64.W))
    val mtip    = Output(Bool())
  })

  private val mtime    = RegInit(0.U(64.W))
  private val mtimecmp = RegInit(0.U(64.W))
  io.mtime := mtime
  io.mtip  := mtime > mtimecmp
  mtime    := mtime + 1.U

  when(io.clintIO.wen) {
    when(io.clintIO.addr === 0.U) { mtime := io.clintIO.wdata }
    when(io.clintIO.addr === 1.U) { mtimecmp := io.clintIO.wdata }
  }

  io.clintIO.rdata := Mux(io.clintIO.addr === 0.U, mtime, mtimecmp)
}

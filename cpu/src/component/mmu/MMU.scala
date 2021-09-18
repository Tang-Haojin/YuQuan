package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._
import cpu.cache._

class MMU(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val ifIO     = new PipelineIO(32)
    val memIO    = new PipelineIO
    val icacheIO = Flipped(new CpuIO(32))
    val dcacheIO = Flipped(new CpuIO)
  })
  io.icacheIO.cpuReq.valid := 0.B
  io.icacheIO.cpuReq.data  := DontCare
  io.icacheIO.cpuReq.rw    := DontCare
  io.icacheIO.cpuReq.wmask := DontCare
  // io.icacheIO.cpuReq.addr

  io.ifIO.pipelineResult.cause      := 0.U
  io.ifIO.pipelineResult.exception  := 0.B
  io.memIO.pipelineResult.cause     := 0.U
  io.memIO.pipelineResult.exception := 0.B

  io.ifIO.pipelineReq.cpuReq       <> io.icacheIO.cpuReq
  io.ifIO.pipelineResult.cpuResult <> io.icacheIO.cpuResult
  when(io.ifIO.pipelineReq.vm) {

  }

  io.memIO.pipelineReq.cpuReq       <> io.dcacheIO.cpuReq
  io.memIO.pipelineResult.cpuResult <> io.dcacheIO.cpuResult
  when(io.memIO.pipelineReq.vm) {

  }
}

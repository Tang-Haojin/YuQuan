package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._

class PipelineReq(implicit p: Parameters) extends YQBundle {
  val vm     = Bool()
  val cpuReq = new cpu.cache.CpuReq
}

class PipelineResult(implicit p: Parameters) extends YQBundle {
  val exception = Bool()
  val cause     = UInt(4.W)
  val cpuResult = new cpu.cache.CpuResult
}

class PipelineIO(datalen: Int = 64)(implicit p: Parameters) extends YQBundle {
  val pipelineReq    = Input (new PipelineReq)
  val pipelineResult = Output(new PipelineResult)
}

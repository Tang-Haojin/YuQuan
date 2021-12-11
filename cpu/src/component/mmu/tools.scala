package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.cache._

class PipelineReq(implicit p: Parameters) extends YQBundle with CacheParams {
  val cpuReq = new cpu.cache.CpuReq()(p.alterPartial({ case utils.ALEN => valen }))
  val flush  = Bool()
  val offset = UInt(Offset.W)
}

class PipelineResult(datalen: Int = 64)(implicit p: Parameters) extends YQBundle {
  val exception  = Bool()
  val cause      = UInt(4.W)
  val cpuResult  = new cpu.cache.CpuResult(datalen)
  val fromMem    = Bool()
  val isMMIO     = if (Debug) Bool() else null
  val crossCache = Bool()
}

class PipelineIO(datalen: Int = 64)(implicit p: Parameters) extends YQBundle {
  val pipelineReq    = Input (new PipelineReq)
  val pipelineResult = Output(new PipelineResult(datalen))
}

class Vaddr(implicit p: Parameters) extends YQBundle {
  val higher = UInt((valen - 12 - 3 * 9).W)
  val vpn    = Vec(3, UInt(9.W))
  val offset = UInt(12.W)

  def :=(that: UInt): Unit = this := that(valen - 1, 0).asTypeOf(new Vaddr)
  def getHigher: UInt = higher ## vpn(2)(9 - 1)
}

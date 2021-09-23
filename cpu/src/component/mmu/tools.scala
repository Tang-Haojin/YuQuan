package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._

class PipelineReq(implicit p: Parameters) extends YQBundle {
  val cpuReq = new cpu.cache.CpuReq()(p.alterPartial({ case utils.ALEN => valen }))
  val reqLen = UInt(2.W)
  val flush  = Bool()
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

class Vaddr(implicit p: Parameters) extends YQBundle {
  val vpn    = Vec(3, UInt(9.W))
  val offset = UInt(12.W)

  def :=(that: UInt): Unit = this := that(3 * 9 + 12).asTypeOf(new Vaddr)
}

object Vaddr {
  import language.implicitConversions
  implicit def Vaddr2UInt(x: Vaddr): UInt = Fill(x.xlen - 3 * 9 - 12, x.vpn(2)(8)) ## x.asUInt()
}

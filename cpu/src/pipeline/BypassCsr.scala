package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class BypassCsr(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val idIO = new YQBundle {
      val bits  = Flipped(new IDOutput)
      val valid = Input(Bool())
    }
    val exIO = new YQBundle {
      val bits  = Flipped(new EXOutput)
      val valid = Input(Bool())
    }
    val memIO = new YQBundle {
      val bits  = Flipped(new MEMOutput)
      val valid = Input(Bool())
    }
    val isWait  = Output(Bool())
    val isPriv  = Output(Bool())
    val isSatp  = Output(Bool())
  })
  private val id  = (!io.idIO.bits.isPriv  && !io.idIO.bits.isWcsr && (io.idIO.bits.special =/= ExecSpecials.exception || isZmb.B ))  || !io.idIO.valid
  private val ex  = (!io.exIO.bits.isPriv  && !io.exIO.bits.isWcsr)  || !io.exIO.valid
  private val mem = (!io.memIO.bits.isPriv && !io.memIO.bits.isWcsr) || !io.memIO.valid
  io.isWait := Mux(id && ex && mem, 0.B, 1.B)
  io.isPriv := io.idIO.bits.isPriv && io.idIO.valid || io.exIO.bits.isPriv && io.exIO.valid || io.memIO.bits.isPriv && io.memIO.valid
  io.isSatp := io.idIO.bits.isSatp && io.idIO.valid || io.exIO.bits.isSatp && io.exIO.valid || io.memIO.bits.isSatp && io.memIO.valid
}

class BypassCsrPipelineBundle(implicit p: Parameters) extends YQBundle {
  val isPriv = Bool()
  val wcar   = Vec(RegConf.writeCsrsPort, UInt(12.W))
  val valid  = Bool()
}

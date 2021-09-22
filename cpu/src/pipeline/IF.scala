package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import utils._
import cpu._
import cpu.cache._
import cpu.tools._
import cpu.component.mmu._

class IF(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val immu   = Flipped(new PipelineIO(32))
    val nextVR = Flipped(new LastVR)
    val output = new IFOutput
    val jmpBch = Input(Bool())
    val jbAddr = Input(UInt(alen.W))
  })

  private class csrsAddr(implicit val p: Parameters) extends CPUParams with cpu.privileged.CSRsAddr
  private val csrsAddr = new csrsAddr

  private val MEMBase = if (UseFlash) SPIFLASH.BASE else DRAM.BASE
  private val instr   = RegInit(0x00000013.U(32.W))
  private val pc      = RegInit(MEMBase.U(alen.W))
  private val NVALID  = RegInit(0.B)
  private val except  = RegInit(0.B)
  private val cause   = RegInit(0.U(4.W))
  private val pause   = RegInit(0.B)

  private val wireInstr  = WireDefault(UInt(32.W), instr);      io.output.instr  := wireInstr
  private val wirePC     = WireDefault(UInt(alen.W), pc - 4.U); io.output.pc     := wirePC
  private val wireNVALID = WireDefault(Bool(), NVALID);         io.nextVR.VALID  := wireNVALID
  private val wireNewPC  = WireDefault(UInt(alen.W), pc)
  private val wireExcept = WireDefault(Bool(), except);         io.output.except := wireExcept
  private val wireCause  = WireDefault(UInt(4.W), cause);       io.output.cause  := wireCause
  private val wirePause  = WireDefault(Bool(), pause)

  private val isSatp = wireInstr(6, 0) === "b1110011".U && (
                         wireInstr(31, 20) === csrsAddr.Satp ||
                         (wireInstr(31, 25) === "b0001001".U && wireInstr(14, 7) === 0.U)
                       )

  io.immu.pipelineReq.cpuReq.data  := DontCare
  io.immu.pipelineReq.cpuReq.rw    := DontCare
  io.immu.pipelineReq.cpuReq.wmask := DontCare
  io.immu.pipelineReq.cpuReq.valid := io.nextVR.READY && !wirePause
  io.immu.pipelineReq.cpuReq.addr  := wireNewPC
  io.immu.pipelineReq.reqLen       := 2.U
  io.immu.pipelineReq.flush        := DontCare

  when(io.immu.pipelineResult.cpuResult.ready) {
    wireInstr  := io.immu.pipelineResult.cpuResult.data
    instr      := wireInstr
    pc         := pc + 4.U
    wirePC     := pc
    wireNewPC  := pc + 4.U
    wireNVALID := 1.B
    wireExcept := io.immu.pipelineResult.exception
    except     := wireExcept
    wireCause  := io.immu.pipelineResult.cause
    cause      := wireCause
    wirePause  := isSatp
    pause      := wirePause
    when(!io.nextVR.READY) { NVALID := 1.B }
  }

  when(io.nextVR.READY && io.nextVR.VALID) {
    pause := 0.B
    when(!io.immu.pipelineResult.cpuResult.ready) { NVALID := 0.B }
    when(io.jmpBch) {
      wireNewPC := io.jbAddr
      pc        := io.jbAddr
    }
  }
}

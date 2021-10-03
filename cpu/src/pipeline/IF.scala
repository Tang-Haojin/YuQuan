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
    val jbAddr = Input(UInt(valen.W))
    val isPriv = Input(Bool())
    val isSatp = Input(Bool())
  })

  private class csrsAddr(implicit val p: Parameters) extends CPUParams with cpu.privileged.CSRsAddr
  private val csrsAddr = new csrsAddr
  private val MEMBase = if (UseFlash) SPIFLASH.BASE else DRAM.BASE

  private val instr   = RegInit(0x00000013.U(32.W))
  private val regPC   = RegInit(MEMBase.U(valen.W))
  private val pc      = RegInit(MEMBase.U(valen.W))
  private val NVALID  = RegInit(0.B)
  private val except  = RegInit(0.B)
  private val cause   = RegInit(0.U(4.W))
  private val pause   = RegInit(0.B)

  private val wirePC    = WireDefault(UInt(valen.W), regPC)
  private val wirePause = WireDefault(Bool(), pause)

  io.output.instr  := instr
  io.output.pc     := pc
  io.output.except := except
  io.output.cause  := cause
  io.nextVR.VALID  := NVALID

  private val wireInstr = io.immu.pipelineResult.cpuResult.data
  private val isSatp = wireInstr(6, 0) === "b1110011".U && (
                         wireInstr(31, 20) === csrsAddr.Satp    ||
                         wireInstr(31, 20) === csrsAddr.Mstatus ||
                         wireInstr(31, 20) === csrsAddr.Sstatus ||
                         (wireInstr(31, 25) === "b0001001".U && wireInstr(14, 7) === 0.U)
                       )

  io.immu.pipelineReq.cpuReq.data  := DontCare
  io.immu.pipelineReq.cpuReq.rw    := DontCare
  io.immu.pipelineReq.cpuReq.wmask := DontCare
  io.immu.pipelineReq.cpuReq.valid := io.nextVR.READY && !wirePause && !io.isPriv && !io.isSatp
  io.immu.pipelineReq.cpuReq.addr  := wirePC
  io.immu.pipelineReq.cpuReq.size  := 2.U // TODO: Compression instruction
  io.immu.pipelineReq.flush        := DontCare

  when(io.immu.pipelineResult.cpuResult.ready && (!io.nextVR.VALID || io.nextVR.READY)) {
    NVALID    := 1.B
    instr     := io.immu.pipelineResult.cpuResult.data
    pc        := regPC
    wirePC    := regPC + 4.U
    regPC     := regPC + 4.U
    except    := io.immu.pipelineResult.exception
    cause     := io.immu.pipelineResult.cause
    wirePause := isSatp
    pause     := wirePause
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    pause  := 0.B
    except := 0.B
    instr  := 0x00000013.U
    NVALID := 0.B
  }

  when(io.immu.pipelineResult.cpuResult.ready && io.immu.pipelineResult.exception && io.immu.pipelineResult.fromMem) {
    except := 1.B
    cause  := io.immu.pipelineResult.cause
    when(io.jmpBch) { pc := io.jbAddr }
  }

  when(io.jmpBch && regPC =/= io.jbAddr) {
    regPC  := io.jbAddr
    wirePC := io.jbAddr
  }
}

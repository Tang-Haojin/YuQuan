package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import utils._
import cpu._
import cpu.tools._
import cpu.component.mmu._

class IF(implicit p: Parameters) extends YQModule with cpu.privileged.LACSRsAddr {
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
  private val MEMBase = if (isLxb) 0x1C000000L else if (UseFlash) SPIFLASH.BASE else DRAM.BASE

  private val instr      = RegInit((if (isLxb) 0x03400000 else 0x00000013).U(32.W))
  private val instrCode  = RegInit(0x13.U(7.W))
  private val rs         = RegInit(VecInit(0.U(5.W), 0.U(5.W)))
  private val rd         = RegInit(0.U(5.W))
  private val regPC      = RegInit(MEMBase.U(valen.W))
  private val pc         = RegInit(MEMBase.U(valen.W))
  private val NVALID     = RegInit(0.B)
  private val except     = RegInit(0.B)
  private val memExcept  = RegInit(0.B)
  private val cause      = RegInit(0.U(4.W))
  private val crossCache = RegInit(0.B)
  private val pause      = RegInit(0.B)

  private val wirePC    = WireDefault(UInt(valen.W), regPC)

  io.output.instr      := instr
  io.output.instrCode  := instrCode
  io.output.rs         := rs
  io.output.rd         := rd
  io.output.pc         := pc
  io.output.except     := except
  io.output.memExcept  := memExcept
  io.output.cause      := cause
  io.output.crossCache := crossCache
  io.nextVR.VALID      := NVALID

  private val wireInstr = io.immu.pipelineResult.cpuResult.data
  private val wirePause = if (isLxb) {
    wireInstr(31, 24) === "b00000100".U && wireInstr(9, 5) =/= 0.U ||
    wireInstr(31, 24) === "b00000110".U ||
    wireInstr(31, 15) === "b00111000011100101".U
  } else {
    wireInstr(6, 0) === "b1110011".U && (
      (ext('S').B && wireInstr(31, 20) === csrsAddr.Satp)    ||
      (!isZmb.B   && wireInstr(31, 20) === csrsAddr.Mstatus) ||
      (ext('S').B && wireInstr(31, 20) === csrsAddr.Sstatus) ||
      (ext('S').B && wireInstr(31, 25) === "b0001001".U && wireInstr(14, 7) === 0.U)
    )
  }

  io.immu.pipelineReq.cpuReq.data   := DontCare
  io.immu.pipelineReq.cpuReq.rw     := DontCare
  io.immu.pipelineReq.cpuReq.wmask  := DontCare
  io.immu.pipelineReq.cpuReq.revoke := pause
  io.immu.pipelineReq.cpuReq.valid  := io.nextVR.READY && !pause && !io.isPriv && !io.isSatp
  io.immu.pipelineReq.cpuReq.addr   := wirePC
  io.immu.pipelineReq.cpuReq.size   := DontCare
  io.immu.pipelineReq.flush         := DontCare
  io.immu.pipelineReq.offset        := regPC
  io.immu.pipelineReq.tlbOp         := DontCare
  io.immu.pipelineReq.tlbrw         := DontCare
  io.immu.pipelineReq.rASID         := DontCare
  io.immu.pipelineReq.rVA           := DontCare
  io.immu.pipelineReq.cpuReq.noCache.getOrElse(WireDefault(0.B)) := DontCare

  when(io.immu.pipelineResult.cpuResult.ready && (!io.nextVR.VALID || io.nextVR.READY)) {
    NVALID     := 1.B
    instr      := wireInstr
    instrCode  := wireInstr(6, 0)
    rs(0)      := wireInstr(19, 15)
    rs(1)      := wireInstr(24, 20)
    rd         := wireInstr(11, 7)
    pc         := regPC
    wirePC     := regPC + Mux(wireInstr(1, 0).andR || !ext('C').B, 4.U, 2.U)
    regPC      := regPC + Mux(wireInstr(1, 0).andR || !ext('C').B, 4.U, 2.U)
    except     := io.immu.pipelineResult.exception
    memExcept  := 0.B
    cause      := io.immu.pipelineResult.cause
    crossCache := io.immu.pipelineResult.crossCache
    pause      := wirePause
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    pause  := 0.B
    instr  := (if (isLxb) 0x03400000 else 0x00000013).U
    instrCode := 0x13.U
    rs := 0.U.asTypeOf(rs)
    rd := 0.U
    NVALID := 0.B
  }

  when(io.immu.pipelineResult.cpuResult.ready && io.immu.pipelineResult.exception && io.immu.pipelineResult.fromMem) {
    except    := 1.B
    memExcept := 1.B
    cause     := io.immu.pipelineResult.cause
    when(io.jmpBch) { pc := io.jbAddr }
  }

  when(io.jmpBch && regPC =/= io.jbAddr) {
    regPC  := io.jbAddr
    wirePC := io.jbAddr
  }
}

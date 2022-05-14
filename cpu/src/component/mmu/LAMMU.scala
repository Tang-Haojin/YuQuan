package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.cache._
import cpu.privileged._

class LAMMU(implicit p: Parameters) extends AbstractMMU {
  private val idle::walking::writing::Nil = Enum(3)
  private val ifWalking::memWalking::Nil = Enum(2)
  private val stage = RegInit(0.U(2.W))
  private val level = RegInit(0.U(2.W))
  private val tlb = new TLB
  private val crossAddrP = RegInit(0.U((39 - Offset).W))
  private val ifVaddr  = if (ext('S')) io.ifIO.pipelineReq.cpuReq.addr.asTypeOf(new Vaddr) else null
  private val memVaddr = if (ext('S')) io.memIO.pipelineReq.cpuReq.addr.asTypeOf(new Vaddr) else null
  private val vaddr    = if (ext('S')) RegInit(new Vaddr, 0.U.asTypeOf(new Vaddr)) else null
  private val satp     = if (ext('S')) UseSatp(io.satp) else UseSatp()
  private val current  = RegInit(0.U(1.W))
  private val isWrite  = io.memIO.pipelineReq.cpuReq.rw
  private val isSv39_i = if (ext('S')) io.priv <= "b01".U && satp.mode === 8.U else 0.B
  private val isSv39_d = if (ext('S')) Mux(io.mprv, io.mpp, io.priv) <= "b01".U && satp.mode === 8.U else 0.B
  private val (ifDel  , memDel  ) = (RegInit(0.B), RegInit(0.B))
  private val (ifReady, memReady) = (RegInit(0.B), RegInit(0.B))
  private val (ifExcpt, memExcpt) = (RegInit(0.B), RegInit(0.B))
  private val (ifCause, memCause) = (RegInit(0.U(4.W)), RegInit(0.U(4.W)))
  private val (isU_i, isS_i, isM_i) = (io.priv === "b00".U, io.priv === "b01".U, io.priv === "b11".U)
  private val (isU_d, isS_d, isM_d) = (
    Mux(io.mprv, io.mpp, io.priv) === "b00".U,
    Mux(io.mprv, io.mpp, io.priv) === "b01".U,
    Mux(io.mprv, io.mpp, io.priv) === "b11".U
  )
  private val icacheValid = WireDefault(Bool(), io.ifIO.pipelineReq.cpuReq.valid)
  private val dcacheValid = WireDefault(Bool(), io.memIO.pipelineReq.cpuReq.valid)
  private val icacheReady = WireDefault(Bool(), io.icacheIO.cpuResult.ready)
  private val partialInst = RegInit(0.U(16.W))

  when(ifDel) { ifDel := 0.B }; when(memDel) { memDel := 0.B }
  io.revAmo := memDel && memReady && memExcpt

  io.ifIO.pipelineResult.cause       := 0.U
  io.ifIO.pipelineResult.exception   := 0.B
  io.ifIO.pipelineResult.fromMem     := 0.B
  io.ifIO.pipelineResult.crossCache  := 0.B
  io.memIO.pipelineResult.cause      := 0.U
  io.memIO.pipelineResult.exception  := 0.B
  io.memIO.pipelineResult.fromMem    := DontCare
  io.memIO.pipelineResult.crossCache := DontCare

  io.ifIO.pipelineReq.cpuReq        <> io.icacheIO.cpuReq
  io.ifIO.pipelineResult.cpuResult  <> io.icacheIO.cpuResult
  io.memIO.pipelineReq.cpuReq       <> io.dcacheIO.cpuReq
  io.memIO.pipelineResult.cpuResult <> io.dcacheIO.cpuResult
  io.icacheIO.cpuReq.valid  := icacheValid
  io.dcacheIO.cpuReq.valid  := dcacheValid
  io.dcacheIO.cpuReq.revoke := 0.B
  io.ifIO.pipelineResult.cpuResult.ready := icacheReady

  when(ifDel) {
    icacheReady := ifReady
    io.ifIO.pipelineResult.exception := ifExcpt
    io.ifIO.pipelineResult.cause := ifCause
    io.ifIO.pipelineResult.fromMem := memExcpt
  }
  when(memDel) {
    io.memIO.pipelineResult.cpuResult.ready := memReady
    io.memIO.pipelineResult.exception := memExcpt
    io.memIO.pipelineResult.cause := memCause
  }

  if (ext('S') || ext('C')) io.icacheIO.cpuReq.addr := Mux(isSv39_i, tlb.translate(ifVaddr), ifVaddr.asUInt)
  if (ext('S') || ext('C')) io.dcacheIO.cpuReq.addr := Mux(isSv39_d, tlb.translate(memVaddr), memVaddr.asUInt)
  if (ext('S')) when(isSv39_i && !tlb.isHit(ifVaddr)) {
    ifDel := 1.B
    ifReady := 0.B
    icacheValid := 0.B
  }
  if (ext('S')) when(isSv39_d && (!tlb.isHit(memVaddr) || (isWrite && !tlb.isDirty(memVaddr)))) {
    memDel := 1.B
    memReady := 0.B
    dcacheValid := 0.B
  }

  when(io.memIO.pipelineReq.cpuReq.valid && io.memIO.pipelineReq.flush && ext('S').B) {
    tlb.flush
    memDel := !memDel; memReady := 1.B; memCause := 0.U; memExcpt := 0.B
    io.dcacheIO.cpuReq.valid := 0.B
  }.elsewhen(handleMisaln.B && io.memIO.pipelineReq.cpuReq.valid && (
    (io.memIO.pipelineReq.cpuReq.size === 1.U && io.memIO.pipelineReq.cpuReq.addr(0)) ||
    (io.memIO.pipelineReq.cpuReq.size === 2.U && io.memIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) ||
    (io.memIO.pipelineReq.cpuReq.size === 3.U && io.memIO.pipelineReq.cpuReq.addr(2, 0) =/= 0.U && (xlen > 32).B)
  )) {
    io.dcacheIO.cpuReq.valid := 0.B
    MemRaiseException(9.U, false) // ALE
  }.elsewhen(handleMisaln.B && io.ifIO.pipelineReq.cpuReq.valid && io.ifIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U && (!ext('C')).B) {
    icacheValid := 0.B
    IfRaiseException(0.U, false) // Instruction address misaligned
  }.otherwise {
    if (ext('S')) when(isSv39_i && io.ifIO.pipelineReq.cpuReq.valid) {
      when(ifVaddr.getHigher.andR =/= ifVaddr.getHigher.orR) { IfRaiseException(12.U, false); io.icacheIO.cpuReq.valid := 0.B } // Instruction page fault
      .elsewhen(tlb.isHit(ifVaddr)) {
        when(isU_i && !tlb.isUser(ifVaddr) || isS_i && tlb.isUser(ifVaddr)) { IfRaiseException(12.U, false); io.icacheIO.cpuReq.valid := 0.B } // Instruction page fault
        .elsewhen(!tlb.canExec(ifVaddr)) { IfRaiseException(12.U, false); io.icacheIO.cpuReq.valid := 0.B } // Instruction page fault
      }.elsewhen(!dcacheValid && stage === idle) {
        current := ifWalking
        stage := walking
        vaddr := ifVaddr
        level := 2.U
      }
    }

    if (ext('S')) when(isSv39_d && io.memIO.pipelineReq.cpuReq.valid && stage === idle) {
      val willWalk = WireDefault(0.B)
      when(memVaddr.getHigher.andR =/= memVaddr.getHigher.orR) { MemRaiseException(Mux(isWrite, 15.U, 13.U), false); io.dcacheIO.cpuReq.valid := 0.B } // load/store/amo page fault
      .elsewhen(tlb.isHit(memVaddr)) {
        when(isU_d && !tlb.isUser(memVaddr) || isS_d && tlb.isUser(memVaddr) && !io.sum) { MemRaiseException(Mux(isWrite, 15.U, 13.U), false); io.dcacheIO.cpuReq.valid := 0.B } // load/store/amo page fault
        .elsewhen(!isWrite && !tlb.canRead(memVaddr)) { MemRaiseException(13.U, false); io.dcacheIO.cpuReq.valid := 0.B } // load page fault
        .elsewhen(isWrite && !tlb.canWrite(memVaddr)) { MemRaiseException(15.U, false); io.dcacheIO.cpuReq.valid := 0.B } // store/amo page fault
        .elsewhen(isWrite && !tlb.isDirty(memVaddr)) { willWalk := 1.B }
      }.otherwise { willWalk := 1.B }
      when(willWalk) {
        current := memWalking
        stage := walking
        vaddr := memVaddr
        level := 2.U
      }
    }
  }

  when(ifDel && ifExcpt) { ifDel := 0.B; ifCause := 0.U; ifExcpt := 0.B }
  when(memDel && memExcpt) {
    when(!io.jmpBch) { memDel := 0.B; memCause := 0.U; memExcpt := 0.B }
    .otherwise       { memDel := 1.B }
  }

  when(io.jmpBch && stage =/= idle && current === ifWalking && isSv39_i) {
    stage := idle
    ifDel := 0.B
    ifCause := 0.B
    ifExcpt := 0.B
    ifReady := 0.B
    icacheReady := 0.B
    io.dcacheIO.cpuReq.revoke := 1.B
    dcacheValid := 0.B
  }

  private case class IfRaiseException(cause: UInt, isPtw: Boolean = true) {
    if (isPtw) stage := idle
    if (isPtw) dcacheValid := 0.B
    ifDel   := 1.B
    ifReady := 1.B
    ifCause := cause
    ifExcpt := 1.B
  }

  private case class MemRaiseException(cause: UInt, isPtw: Boolean = true) {
    IfRaiseException(cause, isPtw)
    memDel   := 1.B
    memReady := 1.B
    memCause := cause
    memExcpt := 1.B
  }
}

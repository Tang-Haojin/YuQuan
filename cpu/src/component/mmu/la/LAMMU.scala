package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.cache._
import cpu.privileged._

class LAMMU(implicit p: Parameters) extends AbstractMMU {
  val laIO = IO(new Bundle {
    val crmd = Input(new CRMDBundle)
    val dmw  = Input(Vec(2, new DMWBundle))
    val asid = Input(new ASIDBundle)
  })
  private implicit val asid = laIO.asid
  private val tlb = new LATLB
  private val ifVaddr  = io.ifIO.pipelineReq.cpuReq.addr
  private val memVaddr = io.memIO.pipelineReq.cpuReq.addr
  private val isWrite  = io.memIO.pipelineReq.cpuReq.rw
  private val Seq(windowHit_i, windowHit_d) = Seq(io.ifIO.pipelineReq.cpuReq.addr, io.memIO.pipelineReq.cpuReq.addr).map(vaddr =>
    laIO.dmw.map(dmw =>
      vaddr(31, 29) === dmw.VSEG &&
      (laIO.crmd.PLV(0) === 0.U & dmw.PLV0 | laIO.crmd.PLV(0) === 1.U & dmw.PLV3)
    )
  )
  private val (direct_i, window_i, page_i) = (
    laIO.crmd.DA, windowHit_i.map(~laIO.crmd.DA && _), ~laIO.crmd.DA && ~windowHit_i.reduce(_ || _)
  )
  private val (direct_d, window_d, page_d) = (
    laIO.crmd.DA, windowHit_d.map(~laIO.crmd.DA && _), ~laIO.crmd.DA && ~windowHit_d.reduce(_ || _)
  )
  private val (ifDel  , memDel  ) = (RegInit(0.B), RegInit(0.B))
  private val (ifReady, memReady) = (RegInit(0.B), RegInit(0.B))
  private val (ifExcpt, memExcpt) = (RegInit(0.B), RegInit(0.B))
  private val (ifCause, memCause) = (RegInit(0.U(4.W)), RegInit(0.U(4.W)))
  private val icacheValid = WireDefault(Bool(), io.ifIO.pipelineReq.cpuReq.valid)
  private val dcacheValid = WireDefault(Bool(), io.memIO.pipelineReq.cpuReq.valid)
  private val icacheReady = WireDefault(Bool(), io.icacheIO.cpuResult.ready)

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
  io.icacheIO.cpuReq.valid  := icacheValid // FIXME: block pipeline when fetch from noCache (to avoid RAW hazard)
  io.dcacheIO.cpuReq.valid  := dcacheValid
  io.dcacheIO.cpuReq.revoke := 0.B
  io.ifIO.pipelineResult.cpuResult.ready := icacheReady

  io.icacheIO.cpuReq.noCache.get := ~Mux1H(Seq(
    direct_i    -> laIO.crmd.DATF(0),
    window_i(0) -> laIO.dmw(0).MAT(0),
    window_i(1) -> laIO.dmw(1).MAT(0),
    page_i      -> 0.B // TODO
  ))
  io.dcacheIO.cpuReq.noCache.get := ~Mux1H(Seq(
    direct_d    -> laIO.crmd.DATM(0),
    window_d(0) -> laIO.dmw(0).MAT(0),
    window_d(1) -> laIO.dmw(1).MAT(0),
    page_d      -> 0.B // TODO
  ))

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

  private val ifTranslateResult  = tlb.translate(ifVaddr)
  private val memTranslateResult = tlb.translate(memVaddr)

  io.icacheIO.cpuReq.addr := Mux1H(Seq(
    direct_i    -> io.ifIO.pipelineReq.cpuReq.addr,
    window_i(0) -> laIO.dmw(0).PSEG ## io.ifIO.pipelineReq.cpuReq.addr(28, 0),
    window_i(1) -> laIO.dmw(1).PSEG ## io.ifIO.pipelineReq.cpuReq.addr(28, 0),
    page_i      -> ifTranslateResult.paddr
  ))
  io.dcacheIO.cpuReq.addr := Mux1H(Seq(
    direct_d    -> io.memIO.pipelineReq.cpuReq.addr,
    window_d(0) -> laIO.dmw(0).PSEG ## io.memIO.pipelineReq.cpuReq.addr(28, 0),
    window_d(1) -> laIO.dmw(1).PSEG ## io.memIO.pipelineReq.cpuReq.addr(28, 0),
    page_d      -> memTranslateResult.paddr
  ))

  when(io.memIO.pipelineReq.cpuReq.valid && io.memIO.pipelineReq.flush && ext('S').B) {
    // TODO: tlb.flush
    memDel := !memDel; memReady := 1.B; memCause := 0.U; memExcpt := 0.B
    io.dcacheIO.cpuReq.valid := 0.B
  }.elsewhen(handleMisaln.B && io.memIO.pipelineReq.cpuReq.valid && (
    (io.memIO.pipelineReq.cpuReq.size === 1.U && io.memIO.pipelineReq.cpuReq.addr(0)) ||
    (io.memIO.pipelineReq.cpuReq.size === 2.U && io.memIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) ||
    (io.memIO.pipelineReq.cpuReq.size === 3.U && io.memIO.pipelineReq.cpuReq.addr(2, 0) =/= 0.U && (xlen > 32).B)
  )) {
    MemRaiseException(0x9.U, false) // ALE
  }.elsewhen(handleMisaln.B && io.ifIO.pipelineReq.cpuReq.valid && io.ifIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) {
    IfRaiseException(0x8.U, false) // ADEF
  }.otherwise {
    when(page_i && io.ifIO.pipelineReq.cpuReq.valid) {
      when(ifTranslateResult.hit) {
        when(!ifTranslateResult.v) {
          IfRaiseException(0x3.U, false) // PIF
        }.elsewhen(laIO.crmd.PLV(0) && ~ifTranslateResult.plv(0)) {
          IfRaiseException(0x7.U, false) // PPI
        }
      }.otherwise {
        IfRaiseException(0x6.U, false) // TLBR
      }
    }
    when(page_d && io.memIO.pipelineReq.cpuReq.valid) {
      when(memTranslateResult.hit) {
        when(!memTranslateResult.v) {
          MemRaiseException(Mux(isWrite, 0x2.U, 0x1.U), false) // Mux(isWrite, PIS, PIL)
        }.elsewhen(laIO.crmd.PLV(0) && ~memTranslateResult.plv(0)) {
          MemRaiseException(0x7.U, false) // PPI
        }.elsewhen(isWrite && !memTranslateResult.d) {
          MemRaiseException(0x4.U, false) // PME
        }
      }.otherwise {
        MemRaiseException(0x6.U, false) // TLBR
      }
    }
  }

  when(ifDel && ifExcpt) { ifDel := 0.B; ifCause := 0.U; ifExcpt := 0.B }
  when(memDel && memExcpt) {
    when(!io.jmpBch) { memDel := 0.B; memCause := 0.U; memExcpt := 0.B }
    .otherwise       { memDel := 1.B }
  }

  private case class IfRaiseException(cause: UInt, isPtw: Boolean = true) {
    icacheValid := 0.B
    ifDel       := 1.B
    ifReady     := 1.B
    ifCause     := cause
    ifExcpt     := 1.B
  }

  private case class MemRaiseException(cause: UInt, isPtw: Boolean = true) {
    IfRaiseException(cause, isPtw)
    dcacheValid := 0.B
    memDel      := 1.B
    memReady    := 1.B
    memCause    := cause
    memExcpt    := 1.B
  }
}

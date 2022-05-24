package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.cache._
import cpu.privileged._
import utils._

class LAMMU(implicit p: Parameters) extends AbstractMMU {
  val laIO = IO(Flipped(new LACSRMMUBundle))
  private implicit val asid = laIO.read.asid
  private val rand = MaximalPeriodGaloisLFSR(log2Ceil(TlbEntries))
  private val tlb = new LATLB
  private val ifVaddr  = io.ifIO.pipelineReq.cpuReq.addr
  private val memVaddr = io.memIO.pipelineReq.cpuReq.addr
  private val isWrite  = io.memIO.pipelineReq.cpuReq.rw
  private val Seq(windowHit_i, windowHit_d) = Seq(io.ifIO.pipelineReq.cpuReq.addr, io.memIO.pipelineReq.cpuReq.addr).map(vaddr =>
    laIO.read.dmw.map(dmw =>
      vaddr(31, 29) === dmw.VSEG &&
      (laIO.read.crmd.PLV(0) === 0.U & dmw.PLV0 | laIO.read.crmd.PLV(0) === 1.U & dmw.PLV3)
    )
  )
  private val (direct_i, window_i, page_i) = (
    laIO.read.crmd.DA, windowHit_i.map(~laIO.read.crmd.DA && _), ~laIO.read.crmd.DA && ~windowHit_i.reduce(_ || _)
  )
  private val (direct_d, window_d, page_d) = (
    laIO.read.crmd.DA, windowHit_d.map(~laIO.read.crmd.DA && _), ~laIO.read.crmd.DA && ~windowHit_d.reduce(_ || _)
  )
  private val (ifDel  , memDel  ) = (RegInit(0.B), RegInit(0.B))
  private val (ifReady, memReady) = (RegInit(0.B), RegInit(0.B))
  private val (ifExcpt, memExcpt) = (RegInit(0.B), RegInit(0.B))
  private val (ifCause, memCause) = (RegInit(0.U(4.W)), RegInit(0.U(4.W)))
  private val icacheValid = WireDefault(Bool(), io.ifIO.pipelineReq.cpuReq.valid)
  private val dcacheValid = WireDefault(Bool(), io.memIO.pipelineReq.cpuReq.valid)
  private val icacheReady = WireDefault(Bool(), io.icacheIO.cpuResult.ready)

  private val ifTranslateResult  = tlb.translate(ifVaddr)
  private val memTranslateResult = tlb.translate(memVaddr)

  when(ifDel) { ifDel := 0.B }; when(memDel) { memDel := 0.B }
  io.revAmo := memDel && memReady && memExcpt

  io.ifIO.pipelineResult.cause       := 0.U
  io.ifIO.pipelineResult.exception   := 0.B
  io.ifIO.pipelineResult.fromMem     := 0.B
  io.ifIO.pipelineResult.crossCache  := 0.B
  io.ifIO.pipelineResult.paddr       := DontCare
  io.memIO.pipelineResult.cause      := 0.U
  io.memIO.pipelineResult.exception  := 0.B
  io.memIO.pipelineResult.fromMem    := DontCare
  io.memIO.pipelineResult.crossCache := DontCare
  io.memIO.pipelineResult.paddr      := io.dcacheIO.cpuReq.addr

  io.ifIO.pipelineReq.cpuReq        <> io.icacheIO.cpuReq
  io.ifIO.pipelineResult.cpuResult  <> io.icacheIO.cpuResult
  io.memIO.pipelineReq.cpuReq       <> io.dcacheIO.cpuReq
  io.memIO.pipelineResult.cpuResult <> io.dcacheIO.cpuResult
  io.icacheIO.cpuReq.valid  := icacheValid // FIXME: block pipeline when fetch from noCache (to avoid RAW hazard)
  io.dcacheIO.cpuReq.valid  := dcacheValid
  io.dcacheIO.cpuReq.revoke := 0.B
  io.ifIO.pipelineResult.cpuResult.ready := icacheReady

  laIO.write := 0.U.asTypeOf(laIO.write)

  io.icacheIO.cpuReq.noCache.get := ~Mux1H(Seq(
    direct_i    -> laIO.read.crmd.DATF(0),
    window_i(0) -> laIO.read.dmw(0).MAT(0),
    window_i(1) -> laIO.read.dmw(1).MAT(0),
    page_i      -> ifTranslateResult.mat(0)
  ))
  io.dcacheIO.cpuReq.noCache.get := ~Mux1H(Seq(
    direct_d    -> laIO.read.crmd.DATM(0),
    window_d(0) -> laIO.read.dmw(0).MAT(0),
    window_d(1) -> laIO.read.dmw(1).MAT(0),
    page_d      -> memTranslateResult.mat(0)
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

  io.icacheIO.cpuReq.addr := Mux1H(Seq(
    direct_i    -> io.ifIO.pipelineReq.cpuReq.addr,
    window_i(0) -> laIO.read.dmw(0).PSEG ## io.ifIO.pipelineReq.cpuReq.addr(28, 0),
    window_i(1) -> laIO.read.dmw(1).PSEG ## io.ifIO.pipelineReq.cpuReq.addr(28, 0),
    page_i      -> ifTranslateResult.paddr
  ))
  io.dcacheIO.cpuReq.addr := Mux1H(Seq(
    direct_d    -> io.memIO.pipelineReq.cpuReq.addr,
    window_d(0) -> laIO.read.dmw(0).PSEG ## io.memIO.pipelineReq.cpuReq.addr(28, 0),
    window_d(1) -> laIO.read.dmw(1).PSEG ## io.memIO.pipelineReq.cpuReq.addr(28, 0),
    page_d      -> memTranslateResult.paddr
  ))

  when(ifDel && ifExcpt) { ifDel := 0.B; ifCause := 0.U; ifExcpt := 0.B }
  when(memDel && memExcpt) {
    when(!io.jmpBch) { memDel := 0.B; memCause := 0.U; memExcpt := 0.B }
    .otherwise       { memDel := 1.B }
  }

  when(handleMisaln.B && io.memIO.pipelineReq.cpuReq.valid && (
    (io.memIO.pipelineReq.cpuReq.size === 1.U && io.memIO.pipelineReq.cpuReq.addr(0)) ||
    (io.memIO.pipelineReq.cpuReq.size === 2.U && io.memIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) ||
    (io.memIO.pipelineReq.cpuReq.size === 3.U && io.memIO.pipelineReq.cpuReq.addr(2, 0) =/= 0.U && (xlen > 32).B)
  )) {
    MemRaiseException(0x9.U) // ALE
  }.elsewhen(handleMisaln.B && io.ifIO.pipelineReq.cpuReq.valid && io.ifIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) {
    IfRaiseException(0x8.U) // ADEF
  }.otherwise {
    when(page_i && io.ifIO.pipelineReq.cpuReq.valid) {
      when(laIO.read.crmd.PLV(0) && ifVaddr(31)) {
        IfRaiseException(0x8.U) // ADEF
      }.elsewhen(ifTranslateResult.hit) {
        when(!ifTranslateResult.v) {
          IfRaiseException(0x3.U) // PIF
        }.elsewhen(laIO.read.crmd.PLV(0) && ~ifTranslateResult.plv(0)) {
          IfRaiseException(0x7.U) // PPI
        }
      }.otherwise {
        IfRaiseException(0x6.U) // TLBR
      }
    }
    when(page_d && io.memIO.pipelineReq.cpuReq.valid) {
      when(laIO.read.crmd.PLV(0) && memVaddr(31)) {
        MemRaiseException(0x5.U) // ADEM
      }.elsewhen(memTranslateResult.hit) {
        when(!memTranslateResult.v) {
          MemRaiseException(Mux(isWrite, 0x2.U, 0x1.U)) // Mux(isWrite, PIS, PIL)
        }.elsewhen(laIO.read.crmd.PLV(0) && ~memTranslateResult.plv(0)) {
          MemRaiseException(0x7.U) // PPI
        }.elsewhen(isWrite && !memTranslateResult.d) {
          MemRaiseException(0x4.U) // PME
        }
      }.otherwise {
        MemRaiseException(0x6.U) // TLBR
      }
    }
  }

  when(io.memIO.pipelineReq.tlbrw) {
    val selectedIndex = Mux(io.memIO.pipelineReq.tlbOp(1, 0) === "b11".U, rand, laIO.read.tlbidx.Index)
    val tlbEntry = tlb.tlbEntries(selectedIndex)
    when(io.memIO.pipelineReq.tlbOp(1, 0) === "b01".U) { // TLBRD
      val tlbelo = VecInit(WireDefault(0.U.asTypeOf(laIO.write.tlbelo)) zip tlbEntry.lo map {
        case (tlbelo, tlbEntryLo) => tlbelo.connect(
          _.V   := tlbEntryLo.v,
          _.D   := tlbEntryLo.d,
          _.PLV := tlbEntryLo.plv,
          _.MAT := tlbEntryLo.mat,
          _.G   := tlbEntry.hi.g,
          _.PPN := tlbEntryLo.ppn
        )
      })
      laIO.write.connect(
        _.valid := 1.B,
        _.tlbehi := Mux(tlbEntry.hi.e, tlbEntry.hi.vppn ## 0.U(13.W), 0.U),
        _.tlbelo := Mux(tlbEntry.hi.e, tlbelo, 0.U.asTypeOf(laIO.write.tlbelo)),
        _.tlbidx := Mux(
          tlbEntry.hi.e,
          laIO.read.tlbidx.replace(
            _.NE := 0.B,
            _.PS := tlbEntry.hi.ps
          ),
          laIO.read.tlbidx.replace(
            _.NE := 1.B,
            _.PS := 0.U
          )
        ),
        _.asid := Mux(
          tlbEntry.hi.e,
          laIO.read.asid.replace(_.ASID := tlbEntry.hi.asid),
          0.U.asTypeOf(laIO.write.asid)
        )
      )
    }
    when(io.memIO.pipelineReq.tlbOp(1) === 1.B) { // TLBWR || TLBFILL
      tlbEntry.hi.connect(
        _.vppn := laIO.read.tlbehi(31, 13),
        _.asid := laIO.read.asid.ASID,
        _.ps   := laIO.read.tlbidx.PS,
        _.g    := laIO.read.tlbelo.map(_.G).reduce(_ & _),
        _.e    := Mux(laIO.read.estat.Ecode === 0x3f.U, 1.B, ~laIO.read.tlbidx.NE)
      )
      tlbEntry.lo zip laIO.read.tlbelo foreach {
        case (tlbEntryLo, tlbelo) => tlbEntryLo.connect(
          _.v   := tlbelo.V,
          _.d   := tlbelo.D,
          _.plv := tlbelo.PLV,
          _.mat := tlbelo.MAT,
          _.ppn := tlbelo.PPN
        )
      }
    }
    when(io.memIO.pipelineReq.tlbOp(1, 0) === "b00".U) { // TLBSRCH
      val tlbsrchResult = tlb.translate(laIO.read.tlbehi)
      laIO.write.connect(
        _.valid := 1.B,
        _.asid := laIO.read.asid,
        _.tlbehi := laIO.read.tlbehi,
        _.tlbelo := laIO.read.tlbelo,
        _.tlbidx := Mux(
          tlbsrchResult.hit,
          laIO.read.tlbidx.replace(
            _.Index := OHToUInt(tlbsrchResult.hitOH),
            _.NE    := 0.B
          ),
          laIO.read.tlbidx.replace(_.NE := 1.B)
        )
      )
    }
  }

  when(io.memIO.pipelineReq.flush) {
    tlb.flush(io.memIO.pipelineReq.rASID, io.memIO.pipelineReq.rVA, io.memIO.pipelineReq.tlbOp)
  }

  private def IfRaiseException(cause: UInt): Unit = {
    icacheValid := 0.B
    ifDel       := 1.B
    ifReady     := 1.B
    ifCause     := cause
    ifExcpt     := 1.B
  }

  private def MemRaiseException(cause: UInt): Unit = {
    IfRaiseException(cause)
    dcacheValid := 0.B
    memDel      := 1.B
    memReady    := 1.B
    memCause    := cause
    memExcpt    := 1.B
  }
}

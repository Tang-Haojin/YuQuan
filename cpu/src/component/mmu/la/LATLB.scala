package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu._
import cpu.cache._
import cpu.privileged._
import cpu.tools._

class LATlbEntryHiBundle(implicit p: Parameters) extends YQBundle {
  val vppn = UInt((valen - 13).W)
  val ps   = UInt(6.W)
  val g    = Bool()
  val asid = UInt(10.W)
  val e    = Bool()
}

class LATlbEntryLoBundle(implicit p: Parameters) extends YQBundle {
  val ppn = UInt((alen - 12).W)
  val plv = UInt(2.W)
  val mat = UInt(2.W)
  val d   = Bool()
  val v   = Bool()
}

class LATlbEntryBundle(implicit p: Parameters) extends Bundle {
  val hi = new LATlbEntryHiBundle
  val lo = Vec(2, new LATlbEntryLoBundle)
}

class TranslateResult(implicit p: Parameters) extends YQBundle with CacheParams {
  val hit   = Bool()
  val hitOH = UInt(TlbEntries.W)
  val paddr = UInt(alen.W)
  val v     = Bool()
  val d     = Bool()
  val mat   = UInt(2.W)
  val plv   = UInt(2.W)
}

class LATLB(implicit val asid: ASIDBundle, implicit val p: Parameters) extends CacheParams with CPUParams {
  val tlbEntries = RegInit(VecInit(Seq.fill(TlbEntries)(0.U.asTypeOf(new LATlbEntryBundle))))

  private val realVppn = tlbEntries.map(entry => Mux(entry.hi.ps === 12.U, entry.hi.vppn, entry.hi.vppn(valen - 14, 9)))

  def translate(vaddr: UInt): TranslateResult = {
    val realVaVppn = tlbEntries.map(entry => Mux(entry.hi.ps === 12.U, vaddr(valen - 1, 13), vaddr(valen - 1, 22)))
    val hitMask = tlbEntries zip realVppn zip realVaVppn map {
      case ((entry, realVppn), realVaVppn) =>
        entry.hi.e && (entry.hi.g || entry.hi.asid === asid.ASID) && (realVppn === realVaVppn)
    }
    val found = Mux1H(hitMask, tlbEntries)
    val foundLo = Mux(Mux(found.hi.ps === 12.U, vaddr(12), vaddr(21)), found.lo(1), found.lo(0))
    Wire(new TranslateResult).connect(
      _.hit   := VecInit(hitMask).asUInt.orR,
      _.hitOH := VecInit(hitMask).asUInt,
      _.paddr := Mux(found.hi.ps === 12.U, foundLo.ppn ## vaddr(11, 0), foundLo.ppn(alen - 13, 9) ## vaddr(20, 0)),
      _.v     := foundLo.v,
      _.d     := foundLo.d,
      _.mat   := foundLo.mat,
      _.plv   := foundLo.plv
    )
  }

  def flush(rASID: UInt, rVA: UInt, op: UInt): Unit = {
    val realRVAVppn = tlbEntries.map(entry => Mux(entry.hi.ps === 12.U, rVA(valen - 1, 13), rVA(valen - 1, 22)))
    val vaEqualMask = realVppn zip realRVAVppn map (x => x._1 === x._2)
    val flushMask = tlbEntries zip vaEqualMask map { case (entry, vaEqual) => Mux1H(Seq(
      (op === 0x0.U) -> 1.B,
      (op === 0x1.U) -> 1.B,
      (op === 0x2.U) -> entry.hi.g,
      (op === 0x3.U) -> !entry.hi.g,
      (op === 0x4.U) -> (!entry.hi.g && entry.hi.asid === rASID),
      (op === 0x5.U) -> (!entry.hi.g && entry.hi.asid === rASID && vaEqual),
      (op === 0x6.U) -> ((entry.hi.g || entry.hi.asid === rASID) && vaEqual)
    ))}
    tlbEntries zip flushMask foreach { case (entry, mask) => when(mask) { entry.hi.e := 0.B } }
  }
}

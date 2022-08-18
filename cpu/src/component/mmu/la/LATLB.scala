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

class TranslateResult(entry: Int)(implicit p: Parameters) extends YQBundle with CacheParams {
  val hit   = Bool()
  val hitOH = UInt(entry.W)
  val paddr = UInt(alen.W)
  val v     = Bool()
  val d     = Bool()
  val mat   = UInt(2.W)
  val plv   = UInt(2.W)
  val found = new LATlbEntryBundle
}

class LATLB(entry: Int)(implicit val asid: ASIDBundle, val p: Parameters, val reset: Bool) extends CacheParams with CPUParams {
  val tlbEntries = Mem(entry, new LATlbEntryBundle)
  private val useTlbEntries = (0 until tlbEntries.length.toInt).map(i => tlbEntries(i))
  when(reset) {
    useTlbEntries.foreach(_.hi.e := 0.B)
  }

  private val realVppn = useTlbEntries.map(entry => Mux(entry.hi.ps === 12.U, entry.hi.vppn, entry.hi.vppn(valen - 14, 9)))

  def translate(vaddr: UInt): TranslateResult = {
    val realVaVppn = useTlbEntries.map(entry => Mux(entry.hi.ps === 12.U, vaddr(valen - 1, 13), vaddr(valen - 1, 22)))
    val hitMask = useTlbEntries zip realVppn zip realVaVppn map {
      case ((entry, realVppn), realVaVppn) =>
        entry.hi.e && (entry.hi.g || entry.hi.asid === asid.ASID) && (realVppn === realVaVppn)
    }
    val found = Mux1H(hitMask, useTlbEntries)
    val foundLos = Mux1H(hitMask, useTlbEntries.map(_.lo))
    val foundLo = Mux(Mux(found.hi.ps === 12.U, vaddr(12), vaddr(21)), foundLos(1), foundLos(0))
    Wire(new TranslateResult(entry)).connect(
      _.hit   := VecInit(hitMask).asUInt.orR,
      _.hitOH := VecInit(hitMask).asUInt,
      _.paddr := Mux(found.hi.ps === 12.U, foundLo.ppn ## vaddr(11, 0), foundLo.ppn(alen - 13, 9) ## vaddr(20, 0)),
      _.v     := foundLo.v,
      _.d     := foundLo.d,
      _.mat   := foundLo.mat,
      _.plv   := foundLo.plv,
      _.found := found
    )
  }

  def flush(rASID: UInt, rVA: UInt, op: UInt): Unit = {
    val realRVAVppn = useTlbEntries.map(entry => Mux(entry.hi.ps === 12.U, rVA(valen - 1, 13), rVA(valen - 1, 22)))
    val vaEqualMask = realVppn zip realRVAVppn map (x => x._1 === x._2)
    val flushMask = useTlbEntries zip vaEqualMask map { case (entry, vaEqual) => Mux1H(Seq(
      (op === 0x0.U) -> 1.B,
      (op === 0x1.U) -> 1.B,
      (op === 0x2.U) -> entry.hi.g,
      (op === 0x3.U) -> !entry.hi.g,
      (op === 0x4.U) -> (!entry.hi.g && entry.hi.asid === rASID),
      (op === 0x5.U) -> (!entry.hi.g && entry.hi.asid === rASID && vaEqual),
      (op === 0x6.U) -> ((entry.hi.g || entry.hi.asid === rASID) && vaEqual)
    ))}
    useTlbEntries zip flushMask foreach { case (entry, mask) => when(mask) { entry.hi.e := 0.B } }
  }
}

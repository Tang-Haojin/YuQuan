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

class TranslateResult(implicit p: Parameters) extends YQBundle {
  val hit   = Bool()
  val paddr = UInt(alen.W)
  val v     = Bool()
  val d     = Bool()
  val mat   = UInt(2.W)
  val plv   = UInt(2.W)
}

class LATLB(implicit val p: Parameters, implicit val asid: ASIDBundle) extends CacheParams with CPUParams {
  private val tlbEntries = RegInit(VecInit(Seq.fill(TlbEntries)(0.U.asTypeOf(new LATlbEntryBundle))))

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
      _.paddr := Mux(found.hi.ps === 12.U, foundLo.ppn ## vaddr(11, 0), foundLo.ppn(alen - 13, 9) ## vaddr(20, 0)),
      _.v     := foundLo.v,
      _.d     := foundLo.d,
      _.mat   := foundLo.mat,
      _.plv   := foundLo.plv
    )
  }
}

class oldLATLB(implicit val p: Parameters) extends CacheParams {
  private val tlbEntries = RegInit(VecInit(Seq.fill(TlbEntries)(0.U.asTypeOf(new TlbEntryBundle))))

  def flush: Unit = tlbEntries.foreach(_.flush)
  def getTlbE(vaddr: Vaddr): Vec[TlbEntryBundle] = VecInit(Seq.tabulate(3)(x => tlbEntries(vaddr.vpn(x)(TlbIndex - 1, 0))))
  def isHitLevel(vaddr: Vaddr): Vec[Bool] = VecInit(Seq.tabulate(3)(x => getTlbE(vaddr)(x).v && (x match {
    case 0 => vaddr.vpn.asUInt             === getTlbE(vaddr)(0).vpn.asUInt
    case 1 => vaddr.vpn(2) ## vaddr.vpn(1) === getTlbE(vaddr)(1).vpn(2) ## getTlbE(vaddr)(1).vpn(1)
    case 2 => vaddr.vpn(2)                 === getTlbE(vaddr)(2).vpn(2)
  }) && getTlbE(vaddr)(x).i === x.U))
  def isHit(vaddr: Vaddr): Bool = isHitLevel(vaddr).asUInt.orR
  def translate(vaddr: Vaddr): UInt = { val tlbEntry = getTlbE(vaddr); Mux1H(Seq(
    isHitLevel(vaddr)(0) -> tlbEntry(0).PPN(2) ## tlbEntry(0).PPN(1) ## tlbEntry(0).PPN(0) ## vaddr.offset,
    isHitLevel(vaddr)(1) -> tlbEntry(1).PPN(2) ## tlbEntry(1).PPN(1) ## vaddr      .vpn(0) ## vaddr.offset,
    isHitLevel(vaddr)(2) -> tlbEntry(2).PPN(2) ## vaddr      .vpn(1) ## vaddr      .vpn(0) ## vaddr.offset
  ))}
  def isDirty (vaddr: Vaddr): Bool = VecInit(Seq.tabulate(3)(x => isHitLevel(vaddr)(x) && getTlbE(vaddr)(x).d)).asUInt.orR
  def isGlobal(vaddr: Vaddr): Bool = VecInit(Seq.tabulate(3)(x => isHitLevel(vaddr)(x) && getTlbE(vaddr)(x).g)).asUInt.orR
  def isUser  (vaddr: Vaddr): Bool = VecInit(Seq.tabulate(3)(x => isHitLevel(vaddr)(x) && getTlbE(vaddr)(x).u)).asUInt.orR
  def canRead (vaddr: Vaddr): Bool = VecInit(Seq.tabulate(3)(x => isHitLevel(vaddr)(x) && getTlbE(vaddr)(x).r)).asUInt.orR
  def canWrite(vaddr: Vaddr): Bool = VecInit(Seq.tabulate(3)(x => isHitLevel(vaddr)(x) && getTlbE(vaddr)(x).w)).asUInt.orR
  def canExec (vaddr: Vaddr): Bool = VecInit(Seq.tabulate(3)(x => isHitLevel(vaddr)(x) && getTlbE(vaddr)(x).x)).asUInt.orR
  def update(vaddr: Vaddr, pte: PTE, level: UInt): Unit = {
    val tlbEntry = tlbEntries(vaddr.vpn(level)(TlbIndex - 1, 0))
    tlbEntry.vpn := vaddr.vpn
    tlbEntry.ppn := pte.ppn
    tlbEntry.v   := 1.B
    tlbEntry.r   := pte.r
    tlbEntry.g   := pte.g
    tlbEntry.u   := pte.u
    tlbEntry.w   := pte.w
    tlbEntry.x   := pte.x
    tlbEntry.d   := pte.d
    tlbEntry.i   := level
  }
}

package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.cache._

class TlbEntryBundle extends Bundle {
  val v   = Bool()
  val r   = Bool()
  val w   = Bool()
  val x   = Bool()
  val u   = Bool()
  val g   = Bool()
  val d   = Bool()
  val i   = UInt(2.W)
  val vpn = Vec(3, UInt(9.W))
  val ppn = UInt(44.W)

  def flush: Unit = this := 0.U.asTypeOf(new TlbEntryBundle)
  def apply(x: Int): Bool = asUInt(x)
  def apply(x: Int, y: Int): UInt = asUInt(x, y)
  def PPN(n: Int): UInt = { require(n >= 0 && n <= 2); n match {
    case 0 => ppn(8, 0)
    case 1 => ppn(17, 9)
    case 2 => ppn(43, 18)
    case _ => 0.U
  }}
}

class TLB(implicit val p: Parameters) extends CacheParams {
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

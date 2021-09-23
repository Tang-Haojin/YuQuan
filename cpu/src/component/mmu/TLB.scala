package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._
import cpu.cache._

class TlbEntryBundle extends Bundle {
  val v   = Bool()
  val r   = Bool()
  val w   = Bool()
  val x   = Bool()
  val u   = Bool()
  val g   = Bool()
  val d   = Bool()
  val vpn = UInt(27.W)
  val ppn = UInt(44.W)

  def flush: Unit = v := 0.B
  def apply(x: Int): Bool = asUInt()(x)
  def apply(x: Int, y: Int): UInt = asUInt()(x, y)
}

class TLB(implicit val p: Parameters) extends CacheParams {
  private val tlbEntries = RegInit(VecInit(Seq.fill(TlbEntries)(0.U.asTypeOf(new TlbEntryBundle))))
  private implicit class impTLB(x: UInt) {
    def vpn: UInt = x(38, 12)
  }

  def flush(): Unit = tlbEntries.foreach(_.flush)
  def apply(x: Int ): TlbEntryBundle = tlbEntries(x)
  def apply(x: UInt): TlbEntryBundle = tlbEntries(x)
  def isHit(vaddr: UInt): Bool = tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).v && vaddr.vpn === getVpn(vaddr)
  def isDirty(vaddr: UInt): Bool = tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).d

  def getVpn(vaddr: UInt): UInt = tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).vpn
  def getPpn(vaddr: UInt): UInt = tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).ppn

  def setVpn(vaddr: UInt): Unit = tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).vpn := vaddr(27 + 12 - 1, 12)
  def setPpn(vaddr: UInt, paddr: UInt): Unit = tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).ppn := paddr(44 + 12 - 1, 12)

  def update(vaddr: UInt, pte: PTE): Unit = {
    val tlbIndex = vaddr(TlbIndex - 1 + 12, 12)
    setVpn(vaddr)
    tlbEntries(tlbIndex).ppn := pte.ppn
    tlbEntries(tlbIndex).v := 1.B
    tlbEntries(tlbIndex).r := pte.r
    tlbEntries(tlbIndex).g := pte.g
    tlbEntries(tlbIndex).u := pte.u
    tlbEntries(tlbIndex).w := pte.w
    tlbEntries(tlbIndex).x := pte.x
    tlbEntries(tlbIndex).d := pte.d
  }
}

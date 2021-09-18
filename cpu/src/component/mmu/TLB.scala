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
  val vpn = UInt(27.W)
  val ppn = UInt(44.W)

  def flush: Unit = v := 0.B
  def apply(x: Int): Bool = asUInt()(x)
  def apply(x: Int, y: Int): UInt = asUInt()(x, y)
}

class TLB(implicit val p: Parameters) extends CacheParams {
  private val tlbEntries = RegInit(VecInit(Seq.fill(TlbEntries)(0.U.asTypeOf(new TlbEntryBundle))))

  def flush: Unit = tlbEntries.foreach(_.flush)
  def apply(x: Int ): TlbEntryBundle = tlbEntries(x)
  def apply(x: UInt): TlbEntryBundle = tlbEntries(x)

  def isHit(vaddr: UInt): Bool = {
    tlbEntries(vaddr(TlbIndex - 1 + 12, 12)).v
  }
}

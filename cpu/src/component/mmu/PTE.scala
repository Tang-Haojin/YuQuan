package cpu.component.mmu

import chisel3._

class PTE extends Bundle {
  val rsw1 = UInt(10.W)
  val ppn2 = UInt(26.W)
  val ppn1 = UInt(9.W)
  val ppn0 = UInt(9.W)
  val rsw0 = UInt(2.W)
  val d    = Bool()
  val a    = Bool()
  val g    = Bool()
  val u    = Bool()
  val x    = Bool()
  val w    = Bool()
  val r    = Bool()
  val v    = Bool()

  def ppn: UInt = ppn2 ## ppn1 ## ppn0
  def :=(that: UInt): Unit = this := that.asTypeOf(new PTE)
}

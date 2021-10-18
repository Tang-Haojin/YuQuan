package cpu.component.mmu

import chisel3._
import chipsalliance.rocketchip.config._

// TODO: add size and access type support
object PMA {
  def apply(addr: UInt, size: UInt, access: UInt): Bool =
    !((addr >= 0x00000000.U && addr < 0x02000000.U) || (addr >= 0x02010001.U && addr < 0x0c000000.U) || (addr >= 0x10002000.U && addr < 0x30000000.U))
}

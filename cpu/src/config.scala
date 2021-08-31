package cpu.config

import chisel3.util._
import utils._

object GeneralConfig {
  val RamSize     = 100 * 1024 * 1024
  val MEMSize     = 100 * 1024 * 1024

  val IALIGN = 32 // compressed instructions are not implemented yet
  val ILEN = 32 // base instruction set supported only

  object STROAGE extends MMAP {
    override val BASE = 0x40000000L
    override val SIZE = 0x08000000L
  }
}

object RegisterConfig {
  val readPortsNum  = 3
  val readCsrsPort  = 8
  val writeCsrsPort = 4
}

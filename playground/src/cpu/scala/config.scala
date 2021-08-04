package cpu.config

import chisel3._
import chisel3.util._
import GeneralConfig._

object GeneralConfig {
  val AluTypeWidth = 5
  val ALEN       = 32
  val XLEN       = sys.env.getOrElse("XLEN", 64).toString.toInt
  val IDLEN      = 4
  val AxSIZE     = log2Ceil(XLEN / 8)
  val HasRVM     = true
  val MEMBase    = 0x80100000L
  val MEMSize    = 100 * 1024 * 1024
  val Extensions = List('I', 'M')
  val IsRealUart = (sys.env.getOrElse("UART", 0).toString.toInt != 0)

  val IALIGN = 32 // compressed instructions are not implemented yet
  val ILEN = 32 // base instruction set supported only

  object UART0_MMIO {
    val BASE = 0x10000000L
    val SIZE = 100
  }

  object CLINT {
    val CLINT = 0x2000000L
    val CLINT_SIZE = 0x10000
    val MTIMECMP = (hartid: Int) => CLINT + 0x4000 + 8 * hartid
    val MTIME = CLINT + 0xBFF8
  }

  object PLIC {
    val PLIC = 0xc000000L
    val PLIC_SIZE = 0x4000000L
    val Isp = (source: Int) => PLIC + 4 * source // Interrupt source n priority
    val Ipb = (source: Int) => PLIC + 0x1000 + 4 * (source / 32) // Interrupt Pending bit
    val Ieb = (source: Int, context: Int) => PLIC + 0x2000 + 0x80 * context + 4 * (source / 32) // Interrupt Enable Bits
    val Ipt = (context: Int) => PLIC + 0x200000 + 0x1000 * context
    val Ic  = (context: Int) => Ipt(context) + 4
  }
}

object RegisterConfig {
  val readPortsNum  = 3
  val readCsrsPort  = 8
  val writeCsrsPort = 4
}

object Debug {
  val Debug      = true
  val debugIO    = Debug && false
  val showReg    = Debug && false
  val partialReg = Debug && true
  val DiffTest   = Debug && (sys.env.getOrElse("DIFF", 1).toString.toInt >= 1)
  val showRegList
    = List(false, true, true, false, false, false, false, false,
           true, false, true, false, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false)
}

object CacheConfig {
  object ICache {
    val CacheSize = 32 * 1024
    val Associativity = 4
    val BlockSize = 64
    val Offset = log2Ceil(BlockSize)
    val IndexSize = CacheSize / Associativity / BlockSize
    val Index = log2Ceil(IndexSize)
    val Tag = XLEN - Offset - Index
    val BurstLen = 8 * BlockSize / XLEN
    val LogBurstLen = log2Ceil(BurstLen)
  }
}

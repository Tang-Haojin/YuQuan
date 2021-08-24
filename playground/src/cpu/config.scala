package cpu.config

import chisel3.util._
import GeneralConfig._
import tools._

object GeneralConfig {
  val AluTypeWidth = 5
  val ALEN        = 32
  val XLEN        = sys.env.getOrElse("XLEN", 64).toString.toInt
  val IDLEN       = 4
  val AxSIZE      = log2Ceil(XLEN / 8)
  val HasRVM      = true
  val UseFlash    = sys.env.getOrElse("FLASH", 0).toString.toInt != 0
  val RamBase     = DRAM.BASE
  val RamSize     = 100 * 1024 * 1024
  val MEMBase     = if (UseFlash) SPI.BASE + 0x100000L else RamBase
  val MEMSize     = 100 * 1024 * 1024
  val Extensions  = List('I', 'M')
  val IsRealUart  = sys.env.getOrElse("UART", 0).toString.toInt != 0
  val UseChipLink = sys.env.getOrElse("CHIPLINK", 0).toString.toInt != 0

  val IALIGN = 32 // compressed instructions are not implemented yet
  val ILEN = 32 // base instruction set supported only

  object UART extends MMAP {
    override val BASE = 0x20001000L
    override val SIZE = 0x1000L
  }

  object CLINT extends MMAP {
    override val BASE = 0x2000000L
    override val SIZE = 0x10000L
    val MTIMECMP = (hartid: Int) => BASE + 0x4000 + 8 * hartid
    val MTIME = BASE + 0xBFF8
  }

  object DRAM extends MMAP {
    override val BASE = 0x80000000L
    override val SIZE = 0x40000000L
  }

  object PLIC extends MMAP {
    override val BASE = 0xc000000L
    override val SIZE = 0x4000000L
    val Isp = (source: Int) => BASE + 4 * source // Interrupt source n priority
    val Ipb = (source: Int) => BASE + 0x1000 + 4 * (source / 32) // Interrupt Pending bit
    val Ieb = (source: Int, context: Int) => BASE + 0x2000 + 0x80 * context + 4 * (source / 32) // Interrupt Enable Bits
    val Ipt = (context: Int) => BASE + 0x200000 + 0x1000 * context
    val Ic  = (context: Int) => Ipt(context) + 4
  }

  object SPI extends MMAP {
    override val BASE = 0x10000000L
    override val SIZE = 0x10001000L // flash + spi controller
    val FLASH_END = 0x1FFFFFFFL
  }

  object NEMU_UART extends MMAP {
    override val BASE = 0xA10003F8L
    override val SIZE = 0x1L
  }

  object CHIPLINK extends MMAP {
    override val BASE = 0x40000000L
    override val SIZE = 0x40000000L
  }

  object STROAGE extends MMAP {
    override val BASE = 0x40000000L
    override val SIZE = 0x08000000L
  }

  object DMAC extends MMAP {
    override val BASE = 0x50000000L
    override val SIZE = 0x1000L
    val DEVICE_ADDR_REG = BASE
    val MEMORY_ADDR_REG = BASE + 8
    val TRANS_LENTH_REG = BASE + 16
    val DMAC_STATUS_REG = BASE + 24
  }
}

object RegisterConfig {
  val readPortsNum  = 3
  val readCsrsPort  = 8
  val writeCsrsPort = 4
}

object Debug {
  val Debug      = true
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
  object DCache {
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

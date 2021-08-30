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

object CacheConfig {
  val XLEN = 64
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

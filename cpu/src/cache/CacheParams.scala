package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._
import cpu._

abstract trait CacheParams {
  implicit val p: Parameters
  
  val Buslen        = p(BUSLEN)
  val CacheSize     = p(CACHE_SIZE)
  val Associativity = p(ASSOCIATIVITY)
  val BlockSize     = p(BLOCK_SIZE)
  val Offset        = p(OFFSET)
  val IndexSize     = p(INDEX_SIZE)
  val Index         = p(INDEX)
  val Tag           = p(TAG)
  val BurstLen      = p(BURST_LEN)
  val LogBurstLen   = p(LOG_BURST_LEN)
  val TlbEntries    = p(TLB_ENTRIES)
  val TlbIndex      = log2Ceil(TlbEntries)
}

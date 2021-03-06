package cpu.cache

import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu._

trait CacheParams {
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
  val FetchFromPeri = p(FETCHFROMPERI)
  val TlbIndex      = log2Ceil(TlbEntries)
}

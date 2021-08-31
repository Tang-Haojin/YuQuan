package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._

class CacheConfig extends Config(CacheConfig.f)

object CacheConfig {
  val f: (View, View, View) => PartialFunction[Any,Any] = (site, here, up) => {
    case CACHE_SIZE    => 4 * 1024 // in byte
    case ASSOCIATIVITY => 4
    case BLOCK_SIZE    => 16       // in byte
    case OFFSET        => log2Ceil(here(BLOCK_SIZE))
    case INDEX_SIZE    => here(CACHE_SIZE) / here(ASSOCIATIVITY) / here(BLOCK_SIZE)
    case INDEX         => log2Ceil(here(INDEX_SIZE))
    case TAG           => up(XLEN) - here(OFFSET) - here(INDEX)
    case BURST_LEN     => 8 * here(BLOCK_SIZE) / up(XLEN)
    case LOG_BURST_LEN => log2Ceil(8 * here(BLOCK_SIZE) / up(XLEN))
  }

  def apply(): CacheConfig = new CacheConfig
}

case object CACHE_SIZE     extends Field[Int]
case object ASSOCIATIVITY  extends Field[Int]
case object BLOCK_SIZE     extends Field[Int]
case object OFFSET         extends Field[Int]
case object INDEX_SIZE     extends Field[Int]
case object INDEX          extends Field[Int]
case object TAG            extends Field[Int]
case object BURST_LEN      extends Field[Int]
case object LOG_BURST_LEN  extends Field[Int]

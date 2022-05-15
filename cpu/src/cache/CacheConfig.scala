package cpu.cache

import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.GEN_NAME

class CacheConfig extends Config(CacheConfig.f)

object CacheConfig {
  val f: (View, View, View) => PartialFunction[Any,Any] = (site, here, up) => {
    case BUSLEN        => up(XLEN)
    case CACHE_SIZE    => site(GEN_NAME) match { case "ysyx" => 4 * 1024; case "zmb" => 32 * 1024; case "lxb" => 4 * 1024 } // in byte
    case ASSOCIATIVITY => 4
    case BLOCK_SIZE    => site(GEN_NAME) match { case "ysyx" => 16; case "zmb" => 128; case "lxb" => 16 } // in byte
    case OFFSET        => log2Ceil(site(BLOCK_SIZE))
    case INDEX_SIZE    => site(CACHE_SIZE) / here(ASSOCIATIVITY) / site(BLOCK_SIZE)
    case INDEX         => log2Ceil(site(INDEX_SIZE))
    case TAG           => site(ALEN) - site(OFFSET) - site(INDEX)
    case BURST_LEN     => 8 * site(BLOCK_SIZE) / up(XLEN)
    case LOG_BURST_LEN => log2Ceil(site(BURST_LEN))
    case FETCHFROMPERI => site(GEN_NAME) match { case "ysyx" => true; case "zmb" => false; case "lxb" => true }
  }

  def apply(): CacheConfig = new CacheConfig
}

case object BUSLEN        extends Field[Int]
case object CACHE_SIZE    extends Field[Int]
case object ASSOCIATIVITY extends Field[Int]
case object BLOCK_SIZE    extends Field[Int]
case object OFFSET        extends Field[Int]
case object INDEX_SIZE    extends Field[Int]
case object INDEX         extends Field[Int]
case object TAG           extends Field[Int]
case object BURST_LEN     extends Field[Int]
case object LOG_BURST_LEN extends Field[Int]
case object FETCHFROMPERI extends Field[Boolean]

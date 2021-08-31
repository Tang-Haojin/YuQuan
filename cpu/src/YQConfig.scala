package cpu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._

class YQConfig extends Config(YQConfig.f)

object YQConfig {
  val f: (View, View, View) => PartialFunction[Any,Any] = (site, here, up) => {
    case XLEN          => 64
    case AxSIZE        => log2Ceil(here(XLEN) / 8)
    case EXTENSIONS    => List('I', 'M')
    case ALEN          => 32
    case IDLEN         => 4
    case MODULE_PREFIX => s"ysyx_210153_"
    case CLINT_MMAP    => new CLINT
    case DRAM_MMAP     => new DRAM
    case ALUTYPEWIDTH  => 5
    case USEFLASH      => true
    case SPIFLASH_MMAP => new PeripheralConfig.SPIFLASH
    case ENABLE_DEBUG  => false
  }

  class CLINT extends MMAP {
    override val BASE = 0x2000000L
    override val SIZE = 0x10000L
    val MTIMECMP = (hartid: Int) => BASE + 0x4000 + 8 * hartid
    val MTIME = BASE + 0xBFF8
  }

  class DRAM extends MMAP {
    override val BASE = 0x80000000L
    override val SIZE = 0x80000000L
  }

  def apply(): YQConfig = new YQConfig
}

case object EXTENSIONS   extends Field[List[Char]]
case object CLINT_MMAP   extends Field[YQConfig.CLINT]
case object DRAM_MMAP    extends Field[YQConfig.DRAM]
case object ALUTYPEWIDTH extends Field[Int]
case object USEFLASH     extends Field[Boolean]
case object ENABLE_DEBUG extends Field[Boolean]

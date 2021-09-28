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
    case EXTENSIONS    => List('I', 'M', 'S', 'A', 'U')
    case ALEN          => 32
    case IDLEN         => 4
    case USRLEN        => 0
    case USEQOS        => 0
    case USEPROT       => 0
    case USECACHE      => 0
    case USELOCK       => 0
    case USEREGION     => 0
    case AXIRENAME     => true
    case MODULE_PREFIX => s"ysyx_210153_"
    case CLINT_MMAP    => new CLINT
    case DRAM_MMAP     => new DRAM
    case PLIC_MMAP     => new PeripheralConfig.PLIC
    case ALUTYPEWIDTH  => 6
    case USEFLASH      => true
    case SPIFLASH_MMAP => new PeripheralConfig.SPIFLASH
    case ENABLE_DEBUG  => false
    case IALIGN        => 32 // compressed instructions are not implemented yet
    case ILEN          => 32 // base instruction set supported only
    case REG_CONF      => new RegConf
    case IS_YSYX       => true
    case IS_ZMB        => false
    case NO_CACHE      => false
    case TLB_ENTRIES   => 16
    case VALEN         => 64
    case USESLAVE      => false
  }

  val zmb: PartialFunction[Any,Any] = {
    case AXIRENAME => false
    case ALEN      => 64
    case IDLEN     => 4
    case USRLEN    => 1
    case USEQOS    => 1
    case USEPROT   => 1
    case USECACHE  => 1
    case USELOCK   => 1
    case USEREGION => 1
    case USEFLASH  => false
    case IS_YSYX   => false
    case IS_ZMB    => true
    case MODULE_PREFIX => s""
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

  class RegConf {
    val readPortsNum  = 3
    val readCsrsPort  = 8
    val writeCsrsPort = 4
  }

  def apply(): YQConfig = new YQConfig
}

case object EXTENSIONS   extends Field[List[Char]]
case object CLINT_MMAP   extends Field[YQConfig.CLINT]
case object DRAM_MMAP    extends Field[YQConfig.DRAM]
case object ALUTYPEWIDTH extends Field[Int]
case object USEFLASH     extends Field[Boolean]
case object ENABLE_DEBUG extends Field[Boolean]
case object IALIGN       extends Field[Int]
case object ILEN         extends Field[Int]
case object REG_CONF     extends Field[YQConfig.RegConf]
case object IS_YSYX      extends Field[Boolean]
case object IS_ZMB       extends Field[Boolean]
case object NO_CACHE     extends Field[Boolean]
case object TLB_ENTRIES  extends Field[Int]
case object VALEN        extends Field[Int]
case object USESLAVE     extends Field[Boolean]

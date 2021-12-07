package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import _root_.cpu._
import _root_.peripheral._

class SimConfig extends Config(SimConfig.basicConfig)

object SimConfig {
  private val basePartFunc: PartialFunction[Any, Any] = {
    case XLEN             => 64
    case ALEN             => 32
    case IDLEN            => 4
    case USRLEN           => 0
    case USEQOS           => 0
    case USEPROT          => 0
    case USECACHE         => 0
    case USELOCK          => 0
    case USEREGION        => 0
    case AXIRENAME        => true
    case EXTENSIONS       => List('I', 'M', 'S', 'A', 'U', 'C')
    case DMAC_MMAP        => new DMAC
    case UART_MMAP        => new PeripheralConfig.UART
    case PLIC_MMAP        => new PeripheralConfig.PLIC
    case SIMPLE_PLIC_MMAP => new YQConfig.SIMPLEPLIC
    case NEMU_UART_MMAP   => new NEMU_UART
    case SD_CARD_MMAP     => new SD_CARD
    case CLINT_MMAP       => new YQConfig.CLINT
    case DRAM_MMAP        => new YQConfig.DRAM
    case SPI_MMAP         => new PeripheralConfig.SPI
    case SPIFLASH_MMAP    => new PeripheralConfig.SPIFLASH
    case ALUTYPEWIDTH     => 5
    case MODULE_PREFIX    => s""
    case REG_CONF         => new YQConfig.RegConf
    case ENABLE_DEBUG     => true
    case TLB_ENTRIES      => 16
    case VALEN            => 64
    case USESLAVE         => false
    case USEPLIC          => true
    case USECLINT         => true
    case HANDLEMISALIGN   => true
  }

  val basicConfig: (View, View, View) => PartialFunction[Any, Any] = (site, here, up) => basePartFunc.orElse({
    case AxSIZE         => log2Ceil(here(XLEN) / 8)
    case USEFLASH       => false
  })

  class DMAC extends MMAP {
    override val BASE = 0x50000000L
    override val SIZE = 0x1000L
    val READ_ADDR_REG = BASE
    val WRITE_ADDR_REG = BASE + 8
    val TRANS_LENTH_REG = BASE + 16
    val DMAC_STATUS_REG = BASE + 24
  }

  class NEMU_UART extends MMAP {
    override val BASE = 0x02010000L
    override val SIZE = 0x1L
  }

  class SD_CARD extends MMAP {
    override val BASE = 0x02020000L
    override val SIZE = 0x1000L
  }

  def apply(): SimConfig = new SimConfig
}

case object DMAC_MMAP      extends Field[SimConfig.DMAC]
case object NEMU_UART_MMAP extends Field[SimConfig.NEMU_UART]
case object SD_CARD_MMAP   extends Field[SimConfig.SD_CARD]

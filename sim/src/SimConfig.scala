package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import _root_.cpu._
import _root_.peripheral._

class SimConfig extends Config(SimConfig.basicConfig)
class SimChipLinkConfig extends Config(SimConfig.chipLinkConfig)

object SimConfig {
  private val basePartFunc: PartialFunction[Any, Any] = {
    case XLEN           => 64
    case ALEN           => 32
    case IDLEN          => 4
    case EXTENSIONS     => List('I', 'M')
    case DMAC_MMAP      => new DMAC
    case UART_MMAP      => new PeripheralConfig.UART
    case PLIC_MMAP      => new PeripheralConfig.PLIC
    case CHIPLINK_MMAP  => new PeripheralConfig.CHIPLINK
    case NEMU_UART_MMAP => new NEMU_UART
    case CLINT_MMAP     => new YQConfig.CLINT
    case DRAM_MMAP      => new YQConfig.DRAM
    case SPI_MMAP       => new PeripheralConfig.SPI
    case ALUTYPEWIDTH   => 5
    case RAMSIZE        => 100L * 1024 * 1024
    case MODULE_PREFIX  => s""
  }

  val basicConfig: (View, View, View) => PartialFunction[Any, Any] = (site, here, up) => basePartFunc.orElse({
    case AxSIZE         => log2Ceil(here(XLEN) / 8)
    case ISREALUART     => false
    case USECHIPLINK    => false
    case USEFLASH       => false
  })
  
  val chipLinkConfig: (View, View, View) => PartialFunction[Any, Any] = (site, here, up) => basePartFunc.orElse({
    case AxSIZE         => log2Ceil(here(XLEN) / 8)
    case ISREALUART     => true
    case USECHIPLINK    => true
    case USEFLASH       => false
  })

  class DMAC extends MMAP {
    override val BASE = 0x50000000L
    override val SIZE = 0x1000L
    val DEVICE_ADDR_REG = BASE
    val MEMORY_ADDR_REG = BASE + 8
    val TRANS_LENTH_REG = BASE + 16
    val DMAC_STATUS_REG = BASE + 24
  }

  class NEMU_UART extends MMAP {
    override val BASE = 0xA10003F8L
    override val SIZE = 0x1L
  }

  def apply(): SimConfig = new SimConfig
}

case object DMAC_MMAP      extends Field[SimConfig.DMAC]
case object NEMU_UART_MMAP extends Field[SimConfig.NEMU_UART]
case object ISREALUART     extends Field[Boolean]
case object USECHIPLINK    extends Field[Boolean]
package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import _root_.cpu._
import _root_.peripheral._

class SimConfig extends Config(SimConfig.f)

object SimConfig {
  val f: (View, View, View) => PartialFunction[Any,Any] = (site, here, up) => {
    case XLEN             => 64
    case ALEN             => 32
    case IDLEN            => 4
    case USRLEN           => 0
    case USEQOS           => 0
    case USEPROT          => 0
    case USECACHE         => 0
    case USELOCK          => 0
    case USEREGION        => 0
    case ISAXI3           => false
    case AXIRENAME        => true
    case EXTENSIONS       => site(GEN_NAME) match { case "ysyx" => List('I', 'M', 'S', 'A', 'U', 'C'); case "zmb" => List('I', 'M') }
    case DMAC_MMAP        => new DMAC
    case UART_MMAP        => new UART
    case SIMPLE_PLIC_MMAP => new YQConfig.SIMPLEPLIC
    case NEMU_UART_MMAP   => new NEMU_UART
    case ZMB_UART_MMAP    => new ZMB_UART
    case SD_CARD_MMAP     => new SD_CARD
    case CLINT_MMAP       => new YQConfig.CLINT
    case DRAM_MMAP        => new YQConfig.DRAM
    case SPI_MMAP         => new PeripheralConfig.SPI
    case SPIFLASH_MMAP    => new PeripheralConfig.SPIFLASH
    case MODULE_PREFIX    => s""
    case REG_CONF         => new YQConfig.RegConf
    case ENABLE_DEBUG     => true
    case TLB_ENTRIES      => 16
    case VALEN            => site(GEN_NAME) match { case "ysyx" => 64; case "zmb" => 32 }
    case USESLAVE         => site(GEN_NAME) match { case "ysyx" => true; case "zmb" => false }
    case USEPLIC          => site(GEN_NAME) match { case "ysyx" => true; case "zmb" => false }
    case USECLINT         => site(GEN_NAME) match { case "ysyx" => true; case "zmb" => false }
    case HANDLEMISALIGN   => site(GEN_NAME) match { case "ysyx" => true; case "zmb" => false }
    case USEXILINX        => site(GEN_NAME) match { case "ysyx" => false; case "zmb" => true }
    case USEDIFFTEST      => false
    case USEPUBRAM        => false
    case USEFLASH         => false
  }

  class UART extends MMAP {
    override val BASE = 0x10000000L
    override val SIZE = 0x1000L
  }

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

  class ZMB_UART extends MMAP {
    override val BASE = 0x40600000L
    override val SIZE = 0x1000L
  }

  def apply(): SimConfig = new SimConfig
}

case object DMAC_MMAP      extends Field[SimConfig.DMAC]
case object NEMU_UART_MMAP extends Field[SimConfig.NEMU_UART]
case object SD_CARD_MMAP   extends Field[SimConfig.SD_CARD]
case object UART_MMAP      extends Field[SimConfig.UART]
case object ZMB_UART_MMAP  extends Field[SimConfig.ZMB_UART]

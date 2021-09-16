package peripheral

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

class PeripheralConfig extends Config(PeripheralConfig.f)

object PeripheralConfig {
  val f: (View, View, View) => PartialFunction[Any, Any] = (site, here, up) => {
    case XLEN          => 64
    case AxSIZE        => log2Ceil(here(XLEN) / 8)
    case ALEN          => 32
    case IDLEN         => 4
    case USRLEN        => 0
    case USEQOS        => 0
    case USEPROT       => 0
    case USECACHE      => 0
    case USELOCK       => 0
    case USEREGION     => 0
    case UART_MMAP     => new UART
    case PLIC_MMAP     => new PLIC
    case CHIPLINK_MMAP => new CHIPLINK
    case SPI_MMAP      => new SPI
  }

  class UART extends MMAP {
    override val BASE = 0x10000000L
    override val SIZE = 0x1000L
  }
  
  class PLIC extends MMAP {
    override val BASE = 0xc000000L
    override val SIZE = 0x4000000L
    val Isp = (source: Int) => BASE + 4 * source // Interrupt source n priority
    val Ipb = (source: Int) => BASE + 0x1000 + 4 * (source / 32) // Interrupt Pending bit
    val Ieb = (source: Int, context: Int) => BASE + 0x2000 + 0x80 * context + 4 * (source / 32) // Interrupt Enable Bits
    val Ipt = (context: Int) => BASE + 0x200000 + 0x1000 * context // threshold
    val Ic  = (context: Int) => Ipt(context) + 4 // claim & complete

    val SIeb = (source: Int, context: Int) => BASE + 0x2080 + 0x80 * context + 4 * (source / 32) // Supervisor Interrupt Enable Bits
    val SIpt = (context: Int) => BASE + 0x201000 + 0x1000 * context // supervisor threshold
    val SIc  = (context: Int) => SIpt(context) + 4 // supervisor claim & complete
  }

  class CHIPLINK extends MMAP {
    override val BASE = 0x40000000L
    override val SIZE = 0x40000000L
  }
  
  class SPI extends MMAP {
    override val BASE = 0x10001000L
    override val SIZE = 0x1000L // flash + spi controller
  }
  
  class SPIFLASH extends MMAP {
    override val BASE = 0x30000000L
    override val SIZE = 0x10000000L // flash + spi controller
  }

  def apply(): PeripheralConfig = new PeripheralConfig
}

case object UART_MMAP     extends Field[PeripheralConfig.UART]
case object PLIC_MMAP     extends Field[PeripheralConfig.PLIC]
case object CHIPLINK_MMAP extends Field[PeripheralConfig.CHIPLINK]
case object SPI_MMAP      extends Field[PeripheralConfig.SPI]
case object SPIFLASH_MMAP extends Field[PeripheralConfig.SPIFLASH]

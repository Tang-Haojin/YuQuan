package peripheral

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

class PeripheralConfig extends Config(PeripheralConfig.f)

object PeripheralConfig {
  val f: (View, View, View) => PartialFunction[Any, Any] = (site, here, up) => {
    case XLEN          => 64
    case ALEN          => 32
    case IDLEN         => 4
    case USRLEN        => 0
    case USEQOS        => 0
    case USEPROT       => 0
    case USECACHE      => 0
    case USELOCK       => 0
    case USEREGION     => 0
    case ISAXI3        => false
    case SPI_MMAP      => new SPI
  }

  class SPI extends MMAP {
    override val BASE = 0x10001000L
    override val SIZE = 0x1000L // spi controller
  }
  
  class SPIFLASH extends MMAP {
    override val BASE = 0x30000000L
    override val SIZE = 0x10000000L // flash
  }

  def apply(): PeripheralConfig = new PeripheralConfig
}

case object SPI_MMAP      extends Field[PeripheralConfig.SPI]
case object SPIFLASH_MMAP extends Field[PeripheralConfig.SPIFLASH]

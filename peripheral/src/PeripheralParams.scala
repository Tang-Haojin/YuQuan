package peripheral

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

abstract trait PeripheralParams extends UtilsParams {
  val UART     = p(UART_MMAP)
  val PLIC     = p(PLIC_MMAP)
  val SPI      = p(SPI_MMAP)
  val SPIFLASH = p(SPIFLASH_MMAP)
}

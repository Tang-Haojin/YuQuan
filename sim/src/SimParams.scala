package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import _root_.cpu.CPUParams
import _root_.peripheral._

abstract trait SimParams extends CPUParams with PeripheralParams {
  val DMAC        = p(DMAC_MMAP)
  val NEMU_UART   = p(NEMU_UART_MMAP)
  val ZMB_UART    = p(ZMB_UART_MMAP)
  val SD_CARD     = p(SD_CARD_MMAP)
  val UART        = p(UART_MMAP)

  override val SPI      = p(SPI_MMAP)
  override val SPIFLASH = p(SPIFLASH_MMAP)
}

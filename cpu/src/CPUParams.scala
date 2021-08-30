package cpu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._

abstract trait CPUParams extends UtilsParams {
  val extensions   = p(EXTENSIONS)
  val CLINT        = p(CLINT_MMAP)
  val DRAM         = p(DRAM_MMAP)
  val AluTypeWidth = p(ALUTYPEWIDTH)
  val UseFlash     = p(USEFLASH)
  val RamSize      = p(RAMSIZE)
  val SPI          = p(SPI_MMAP)
  val Debug        = p(ENABLE_DEBUG)
}

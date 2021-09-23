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
  val SPIFLASH     = p(SPIFLASH_MMAP)
  val RegConf      = p(REG_CONF)
  val Debug        = p(ENABLE_DEBUG)
  val IsYsyx       = p(IS_YSYX)
  val noCache      = p(NO_CACHE)
  val valen        = p(VALEN)
}

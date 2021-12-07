package cpu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._

abstract trait CPUParams extends UtilsParams {
  val extensions   = p(EXTENSIONS)
  val CLINT        = p(CLINT_MMAP)
  val SIMPLEPLIC   = p(SIMPLE_PLIC_MMAP)
  val DRAM         = p(DRAM_MMAP)
  val AluTypeWidth = p(ALUTYPEWIDTH)
  val UseFlash     = p(USEFLASH)
  val SPIFLASH     = p(SPIFLASH_MMAP)
  val RegConf      = p(REG_CONF)
  val Debug        = p(ENABLE_DEBUG)
  val valen        = p(VALEN)
  val useSlave     = p(USESLAVE)
  val usePlic      = p(USEPLIC)
  val useClint     = p(USECLINT)
  val handleMisaln = p(HANDLEMISALIGN)
  val isZmb        = p(GEN_NAME) == "zmb"
  
  def ext(extension: Char): Boolean = extensions.contains(extension)
}

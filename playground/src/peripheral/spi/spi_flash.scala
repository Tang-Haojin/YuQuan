package peripheral.spi

import chisel3._
import chisel3.util._

import cpu.config.GeneralConfig._
import tools._

class SpiFlashIO extends Bundle with ApbSlaveIOTrait with SpiMasterIOTrait

class spi_flash extends BlackBox(Map(
  "flash_addr_start" -> SPI.BASE,
  "flash_addr_end"   -> SPI.FLASH_END,
  "spi_cs_num"       -> 1
)) {
  val io = IO(new SpiFlashIO)
}

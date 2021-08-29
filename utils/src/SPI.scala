package utils

import chisel3._

import chipsalliance.rocketchip.config._

class SpiSlaveIO(implicit val p: Parameters) extends Bundle with SpiSlaveIOTrait

class SpiMasterIO(implicit val p: Parameters) extends Bundle with SpiMasterIOTrait

abstract trait SpiSlaveIOTrait extends UtilsParams {
  val spi_clk     = Input (Bool())
  val spi_cs      = Input (Bool())
  val spi_mosi    = Input (UInt(1.W))
  val spi_miso    = Output(UInt(1.W))
  val spi_irq_out = Input (Bool())
}

abstract trait SpiMasterIOTrait extends UtilsParams {
  val spi_clk     = Output(Bool())
  val spi_cs      = Output(Bool())
  val spi_mosi    = Output(UInt(1.W))
  val spi_miso    = Input (UInt(1.W))
  val spi_irq_out = Output(Bool())
}

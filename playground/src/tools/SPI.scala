package tools

import chisel3._

class SpiSlaveIO extends Bundle with SpiSlaveIOTrait

class SpiMasterIO extends Bundle with SpiMasterIOTrait

trait SpiSlaveIOTrait {
  val spi_clk     = Input (Bool())
  val spi_cs      = Input (Bool())
  val spi_mosi    = Input (UInt(1.W))
  val spi_miso    = Output(UInt(1.W))
  val spi_irq_out = Input (Bool())
}

trait SpiMasterIOTrait {
  val spi_clk     = Output(Bool())
  val spi_cs      = Output(Bool())
  val spi_mosi    = Output(UInt(1.W))
  val spi_miso    = Input (UInt(1.W))
  val spi_irq_out = Output(Bool())
}

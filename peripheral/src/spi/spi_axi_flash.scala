package peripheral.spi

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import utils.axi2apb._
import peripheral._

class SpiAxiFlashIO(implicit val p: Parameters) extends Bundle with PeripheralParams {
  val axi_s = new AxiSlaveIO
  val spi_m = new SpiMasterIO
}

class spi_axi_flash(implicit val p: Parameters) extends RawModule with PeripheralParams {
  val io = IO(new SpiAxiFlashIO)

  val spiFlash = Module(new spi_flash(Map(
    "flash_addr_start" -> SPIFLASH.BASE,
    "flash_addr_end"   -> (SPIFLASH.BASE + SPIFLASH.SIZE - 1),
    "spi_cs_num"       -> 1
  )))
  val axi2apb  = Module(new Axi2Apb)

  io.axi_s <> axi2apb.io.axi_s

  spiFlash.io.pclk    := axi2apb.io.apb_m.pclk
  spiFlash.io.presetn := axi2apb.io.apb_m.presetn
  spiFlash.io.paddr   := axi2apb.io.apb_m.paddr
  spiFlash.io.penable := axi2apb.io.apb_m.penable
  spiFlash.io.psel    := axi2apb.io.apb_m.psel
  spiFlash.io.pwdata  := axi2apb.io.apb_m.pwdata
  spiFlash.io.pwrite  := axi2apb.io.apb_m.pwrite
  spiFlash.io.pwstrb  := axi2apb.io.apb_m.pwstrb

  axi2apb.io.apb_m.pready  := spiFlash.io.pready
  axi2apb.io.apb_m.pslverr := spiFlash.io.pslverr
  axi2apb.io.apb_m.prdata  := spiFlash.io.prdata

  spiFlash.io.spi_miso := io.spi_m.spi_miso

  io.spi_m.spi_clk     := spiFlash.io.spi_clk
  io.spi_m.spi_cs      := spiFlash.io.spi_cs
  io.spi_m.spi_irq_out := spiFlash.io.spi_irq_out
  io.spi_m.spi_mosi    := spiFlash.io.spi_mosi
}

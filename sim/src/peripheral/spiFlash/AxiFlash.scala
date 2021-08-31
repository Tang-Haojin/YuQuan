package sim.peripheral.spiFlash

import chisel3._
import chisel3.util._

import utils._
import peripheral._
import peripheral.spi.spi_axi_flash
import cpu.tools._
import chipsalliance.rocketchip.config._

class AxiFlash(implicit p: Parameters) extends YQRawModule {
  val io = IO(new AxiSlaveIO)

  val moduleSpiFlash = Module(new spiFlash)
  val spiAxiFlash    = Module(new spi_axi_flash)

  io <> spiAxiFlash.io.axi_s
  moduleSpiFlash.io <> spiAxiFlash.io.spi_m
}

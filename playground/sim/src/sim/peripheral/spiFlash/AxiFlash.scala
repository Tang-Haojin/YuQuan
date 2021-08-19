package sim.peripheral.spiFlash

import chisel3._
import chisel3.util._

import tools._
import cpu.config.GeneralConfig._
import peripheral._
import peripheral.spi.spi_axi_flash

class AxiFlash extends RawModule {
  val io = IO(new AxiSlaveIO)

  val moduleSpiFlash = Module(new spiFlash)
  val spiAxiFlash    = Module(new spi_axi_flash)

  io <> spiAxiFlash.io.axi_s
  moduleSpiFlash.io <> spiAxiFlash.io.spi_m
}

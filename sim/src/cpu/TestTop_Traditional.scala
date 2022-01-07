package sim.cpu

import chisel3._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._

import cpu._

import sim._
import peripheral.ram._
import peripheral.uart._
import peripheral.spiFlash._
import peripheral.dmac._
import peripheral.sdcard._

class TestTop_Traditional(io: DEBUG, clock: Clock, reset: Reset)(implicit val p: Parameters) extends SimParams {
  val cpu       = Module(new CPU)
  val mem       = Module(new RAM)
  val uart      = Module(new UartSim)
  val spi       = Module(new AxiFlash)
  val sd        = Module(new SDCard)
  val nemu_uart = Module(new Nemu_Uart)
  val zmb_uart  = Module(new Zmb_Uart)
  val dmac      = Module(new DMAC)
  val router    = Module(new ROUTER)

  io <> cpu.io.debug

  cpu.io.master <> router.io.input
  cpu.io.slave  <> dmac.io.toCPU

  router.io.DramIO      <> mem.io.channel
  router.io.UartIO      <> uart.io.channel
  router.io.SpiIO       <> spi.io.channel
  router.io.Nemu_UartIO <> nemu_uart.io.channel
  router.io.Zmb_UartIO  <> zmb_uart.io.channel
  router.io.Dmac        <> dmac.io.fromCPU.channel
  router.io.SdIO        <> sd.io.channel

  cpu.io.interrupt  := uart.io.interrupt

  mem.io.basic.ACLK             := clock
  mem.io.basic.ARESETn          := !reset.asBool
  uart.io.basic.ACLK            := clock
  uart.io.basic.ARESETn         := !reset.asBool
  spi.io.basic.ACLK             := clock
  spi.io.basic.ARESETn          := !reset.asBool
  nemu_uart.io.basic.ACLK       := clock
  nemu_uart.io.basic.ARESETn    := !reset.asBool
  zmb_uart.io.basic.ACLK        := clock
  zmb_uart.io.basic.ARESETn     := !reset.asBool
  dmac.io.fromCPU.basic.ACLK    := clock
  dmac.io.fromCPU.basic.ARESETn := !reset.asBool
  sd.io.basic.ACLK              := clock
  sd.io.basic.ARESETn           := !reset.asBool
  router.io.basic.ACLK          := clock
  router.io.basic.ARESETn       := !reset.asBool
}

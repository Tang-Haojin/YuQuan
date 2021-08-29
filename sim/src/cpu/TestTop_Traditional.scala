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

class TestTop_Traditional(io: DEBUG, clock: Clock, reset: Reset)(implicit val p: Parameters) extends SimParams {
  val cpu       = Module(new CPU)
  val mem       = Module(new RAM)
  val uart      = Module(if (IsRealUart) new UartReal else new UartSim)
  val plic      = Module(new Plic)
  val spi       = Module(new AxiFlash)
  val nemu_uart = Module(new Nemu_Uart)
  val router    = Module(new ROUTER)

  io <> cpu.io.debug

  cpu.io.memAXI  <> router.io.input
  cpu.io.dmaAXI  := DontCare

  router.io.DramIO      <> mem.io.channel
  router.io.UartIO      <> uart.io.channel
  router.io.PLICIO      <> plic.io.channel
  router.io.SpiIO       <> spi.io.channel
  router.io.Nemu_UartIO <> nemu_uart.io.channel

  plic.io.inter     := VecInit(Seq.fill(plic.io.inter.length)(0.B))
  plic.io.inter(10) := uart.io.interrupt
  cpu.io.intr       := plic.io.eip

  cpu.io.basic.ACLK          := clock
  cpu.io.basic.ARESETn       := reset
  mem.io.basic.ACLK          := clock
  mem.io.basic.ARESETn       := reset
  uart.io.basic.ACLK         := clock
  uart.io.basic.ARESETn      := reset
  plic.io.basic.ACLK         := clock
  plic.io.basic.ARESETn      := reset
  spi.io.basic.ACLK          := clock
  spi.io.basic.ARESETn       := reset
  nemu_uart.io.basic.ACLK    := clock
  nemu_uart.io.basic.ARESETn := reset
  router.io.basic.ACLK       := clock
  router.io.basic.ARESETn    := reset
}

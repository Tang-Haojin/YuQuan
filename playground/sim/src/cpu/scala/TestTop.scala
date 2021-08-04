package sim

import chisel3._
import chisel3.util._

import utest._
import chisel3.tester._

import cpu._
import cpu.config.GeneralConfig._
import cpu.peripheral._

class TestTop extends Module {
  val io = IO(new DEBUG)

  val cpu    = Module(new CPU)
  val mem    = Module(new RAM)
  val uart0  = Module(if (IsRealUart) new UartReal else new UartSim)
  val plic   = Module(new Plic)
  val router = Module(new ROUTER)

  io <> cpu.io.debug

  cpu.io.memAXI  <> mem.io.channel
  cpu.io.mmioAXI <> router.io.input
  cpu.io.dmaAXI  := DontCare

  router.io.Uart0IO <> uart0.io.channel
  router.io.PLICIO  <> plic.io.channel

  plic.io.inter     := VecInit(Seq.fill(plic.io.inter.length)(0.B))
  plic.io.inter(10) := uart0.io.interrupt
  cpu.io.intr       := plic.io.eip

  cpu.io.basic.ACLK       := clock
  cpu.io.basic.ARESETn    := reset
  mem.io.basic.ACLK       := clock
  mem.io.basic.ARESETn    := reset
  uart0.io.basic.ACLK     := clock
  uart0.io.basic.ARESETn  := reset
  plic.io.basic.ACLK      := clock
  plic.io.basic.ARESETn   := reset
  router.io.basic.ACLK    := clock
  router.io.basic.ARESETn := reset
}

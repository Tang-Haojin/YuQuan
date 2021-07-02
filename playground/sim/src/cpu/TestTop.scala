package sim

import chisel3._
import chisel3.util._

import utest._
import chisel3.tester._

import cpu._
import cpu.axi._
import cpu.config.GeneralConfig._
import chisel3.internal.firrtl.Node

class TestTop extends Module {
  val io = IO(new DEBUG)

  val cpu = Module(new CPU)
  val mem = Module(new RAM)
  val uart0 = Module(new UART)
  val plic = Module(new Plic)
  val router = Module(new ROUTER)

  io <> cpu.io.debug

  cpu.io.axiWa <> router.io.input.axiWa
  cpu.io.axiWd <> router.io.input.axiWd
  cpu.io.axiWr <> router.io.input.axiWr
  cpu.io.axiRa <> router.io.input.axiRa
  cpu.io.axiRd <> router.io.input.axiRd

  router.io.RamIO.axiWa <> mem.io.axiWa
  router.io.RamIO.axiWd <> mem.io.axiWd
  router.io.RamIO.axiWr <> mem.io.axiWr
  router.io.RamIO.axiRa <> mem.io.axiRa
  router.io.RamIO.axiRd <> mem.io.axiRd

  router.io.Uart0IO.axiWa <> uart0.io.axiWa
  router.io.Uart0IO.axiWd <> uart0.io.axiWd
  router.io.Uart0IO.axiWr <> uart0.io.axiWr
  router.io.Uart0IO.axiRa <> uart0.io.axiRa
  router.io.Uart0IO.axiRd <> uart0.io.axiRd

  router.io.PLICIO.axiWa <> plic.io.axiWa
  router.io.PLICIO.axiWd <> plic.io.axiWd
  router.io.PLICIO.axiWr <> plic.io.axiWr
  router.io.PLICIO.axiRa <> plic.io.axiRa
  router.io.PLICIO.axiRd <> plic.io.axiRd

  plic.io.inter := VecInit(Seq.fill(plic.io.inter.length)(0.B))
  val uart_int = Module(new UartInt)
  uart_int.io.clock := clock
  plic.io.inter(10) := uart_int.io.inter
  cpu.io.eip        := plic.io.eip
  
  cpu.io.basic.ACLK             := clock
  cpu.io.basic.ARESETn          := reset
  mem.io.basic.ACLK             := clock
  mem.io.basic.ARESETn          := reset
  uart0.io.basic.ACLK           := clock
  uart0.io.basic.ARESETn        := reset
  plic.io.basic.ACLK            := clock
  plic.io.basic.ARESETn         := reset
  router.io.input.basic.ACLK    := clock
  router.io.input.basic.ARESETn := reset
}

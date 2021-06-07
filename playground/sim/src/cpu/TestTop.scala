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
  val io = IO(new Bundle {})
  val cpu = Module(new CPU)
  val mem = Module(new RAM)

  cpu.io.axiWa <> mem.io.axiWa
  cpu.io.axiWd <> mem.io.axiWd
  cpu.io.axiWr <> mem.io.axiWr
  cpu.io.axiRa <> mem.io.axiRa
  cpu.io.axiRd <> mem.io.axiRd
  
  cpu.io.basic.ACLK    := clock
  cpu.io.basic.ARESETn := reset
  mem.io.basic.ACLK    := clock
  mem.io.basic.ARESETn := reset
}
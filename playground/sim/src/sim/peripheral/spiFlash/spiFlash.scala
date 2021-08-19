package sim.peripheral.spiFlash

import chisel3._
import chisel3.util._

import tools._

class spiFlash extends BlackBox {
  val io = IO(new SpiSlaveIO)
}

package sim.peripheral.spiFlash

import chisel3._
import chisel3.util._

import utils._
import cpu.tools._
import chipsalliance.rocketchip.config._

class spiFlash(implicit p: Parameters) extends YQBlackBox {
  val io = IO(new SpiSlaveIO)
}

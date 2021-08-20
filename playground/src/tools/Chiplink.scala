package tools

import chisel3._

class ChiplinkMasterIO extends Bundle {
  val clk  = Output(Bool())
  val rst  = Output(Bool())
  val send = Output(Bool())
  val data = Output(UInt(32.W))
}

class ChiplinkIO extends Bundle {
  val c2b = new ChiplinkMasterIO
  val b2c = Flipped(new ChiplinkMasterIO)
}

package utils

import chisel3._
import chipsalliance.rocketchip.config._

class ChiplinkMasterIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val clk  = Output(Bool())
  val rst  = Output(Bool())
  val send = Output(Bool())
  val data = Output(UInt(32.W))
}

class ChiplinkIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val c2b = new ChiplinkMasterIO
  val b2c = Flipped(new ChiplinkMasterIO)
}

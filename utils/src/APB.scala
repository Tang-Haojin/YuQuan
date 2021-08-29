package utils

import chisel3._

import chipsalliance.rocketchip.config._

class ApbSlaveIO(implicit val p: Parameters) extends Bundle with ApbSlaveIOTrait

trait ApbSlaveIOTrait extends UtilsParams {
  val presetn = Input (Bool())
  val pclk    = Input (Clock())
  val psel    = Input (Bool())
  val penable = Input (Bool())
  val pready  = Output(Bool())
  val pslverr = Output(Bool())
  val paddr   = Input (UInt(32.W))
  val pwrite  = Input (Bool())
  val prdata  = Output(UInt(32.W))
  val pwdata  = Input (UInt(32.W))
  val pwstrb  = Input (UInt((32 / 8).W))
}

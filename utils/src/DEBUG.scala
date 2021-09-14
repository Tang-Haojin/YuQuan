package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

class DEBUG(implicit val p: Parameters) extends Bundle with UtilsParams {
  val exit    = Output(UInt(3.W))
  val data    = Output(UInt(xlen.W))
  val wbPC    = Output(UInt(xlen.W))
  val wbValid = Output(Bool())
  val wbRd    = Output(UInt(5.W))
  val wbRcsr  = Output(UInt(12.W))
  val wbMMIO  = Output(Bool())
  val gprs    = Output(Vec(32, UInt(xlen.W)))
}

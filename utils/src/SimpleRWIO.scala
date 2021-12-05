package utils

import chisel3._
import chisel3.util._

class SimpleRWIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val wen   = Input (Bool())
  val addr  = Input (UInt(addrWidth.W))
  val rdata = Output(UInt(dataWidth.W))
  val wdata = Input (UInt(dataWidth.W))
}

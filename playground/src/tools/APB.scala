package tools

import chisel3._
import chisel3.util._

class ApbSlaveIO extends Bundle {
  val PRESETn = Input (Bool())
  val PCLK    = Input (Clock())
  val PSEL    = Input (Bool())
  val PENABLE = Input (Bool())
  val PREADY  = Output(Bool())
  val PSLVERR = Output(Bool())
  val PADDR   = Input (UInt(32.W))
  val PWRITE  = Input (Bool())
  val PRDATA  = Output(UInt(32.W))
  val PWDATA  = Input (UInt(32.W))
  val PWSTRB  = Input (UInt((32 / 8).W))
}

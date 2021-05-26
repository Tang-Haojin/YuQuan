package cpu

import chisel3._
import chisel3.util._

// instruction fetching module
class IF extends Module {
  val io = IO(new Bundle {
    // Global signal
    val in       = Input (UInt( 1.W))
    val ARESETn  = Input (UInt( 1.W))
    // Read address channel signals
    val ARID     = Output(UInt( 4.W))
    val ARADDR   = Output(UInt(32.W))
    val ARLEN    = Output(UInt( 8.W))
    val ARSIZE   = Output(UInt( 3.W))
    val ARBURST  = Output(UInt( 2.W))
    val ARLOCK   = Output(UInt( 2.W))
    val ARCACHE  = Output(UInt( 4.W))
    val ARPORT   = Output(UInt( 3.W))
    val ARQOS    = Output(UInt( 4.W))
    val ARREGION = Output(UInt( 4.W))
    val ARUSER   = Output(UInt( 1.W))
    val ARVALID  = Output(UInt( 1.W))
    val ARREADY  = Input (UInt( 1.W))
    // Read data channel signals
    val RID      = Input (UInt( 4.W))
    val RDATA    = Input (UInt(64.W))
    val RRESP    = Input (UInt( 2.W))
    val RLAST    = Input (UInt( 1.W))
    val RUSER    = Input (UInt( 1.W))
    val RVALID   = Input (UInt( 1.W))
    val RREADY   = Output(UInt( 1.W))
  })

  ???
}
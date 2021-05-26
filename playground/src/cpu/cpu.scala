package cpu

import chisel3._
import chisel3.util._

class Cpu extends Module {
	val io = IO(new Bundle {
    // Global signal
    val in       = Input (UInt( 1.W))
    val ARESETn  = Input (UInt( 1.W))
    // Write address channel signals
    val AWID     = Output(UInt( 4.W))
    val AWADDR   = Output(UInt(32.W))
    val AWLEN    = Output(UInt( 8.W))
    val AWSIZE   = Output(UInt( 3.W))
    val AWBURST  = Output(UInt( 2.W))
    val AWLOCK   = Output(UInt( 2.W))
    val AWCACHE  = Output(UInt( 4.W))
    val AWPORT   = Output(UInt( 3.W))
    val AWQOS    = Output(UInt( 4.W))
    val AWREGION = Output(UInt( 4.W))
    val AWUSER   = Output(UInt( 1.W))
    val AWVALID  = Output(UInt( 1.W))
    val AWREADY  = Input (UInt( 1.W))
    // Write data channel signals
    val WID      = Output(UInt( 0.W))
    val WDATA    = Output(UInt(64.W))
    val WSTRB    = Output(UInt( 4.W))
    val WLAST    = Output(UInt( 1.W))
    val WUSER    = Output(UInt( 1.W))
    val WVALID   = Output(UInt( 1.W))
    val WREADY   = Input (UInt( 1.W))
    // Write response channel signals
    val BID      = Input (UInt( 4.W))
    val BRESP    = Input (UInt( 2.W))
    val BUSER    = Input (UInt( 1.W))
    val BVALID   = Input (UInt( 1.W))
    val BREADY   = Output(UInt( 1.W))
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

  val IF = Module(new IF)
  ???
}
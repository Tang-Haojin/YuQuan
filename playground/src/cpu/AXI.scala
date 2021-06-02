package cpu.axi

import chisel3._
import cpu.config.GeneralConfig._
import cpu.config._

class AXIall extends AXI with AXIwa with AXIwd with AXIwr with AXIra with AXIrd

// Write address channel signals
trait AXIwa {
  val AWID     = Output(UInt(4.W))
  val AWADDR   = Output(UInt(XLEN.W))
  val AWLEN    = Output(UInt(8.W))
  val AWSIZE   = Output(UInt(3.W))
  val AWBURST  = Output(UInt(2.W))
  val AWLOCK   = Output(UInt(2.W))
  val AWCACHE  = Output(UInt(4.W))
  val AWPORT   = Output(UInt(3.W))
  val AWQOS    = Output(UInt(4.W))
  val AWREGION = Output(UInt(4.W))
  val AWUSER   = Output(UInt(1.W))
  val AWVALID  = Output(Bool())
  val AWREADY  = Input (Bool())
}

// Write data channel signals
trait AXIwd {
  val WID      = Output(UInt(0.W))
  val WDATA    = Output(UInt(XLEN.W))
  val WSTRB    = Output(UInt(4.W))
  val WLAST    = Output(Bool())
  val WUSER    = Output(UInt(1.W))
  val WVALID   = Output(Bool())
  val WREADY   = Input (Bool())
}

// Write response channel signals
trait AXIwr {
  val BID      = Input (UInt(4.W))
  val BRESP    = Input (UInt(2.W))
  val BUSER    = Input (UInt(1.W))
  val BVALID   = Input (Bool())
  val BREADY   = Output(Bool())
}

// Read address channel signals
trait AXIra {
  val ARID     = Output(UInt(4.W))
  val ARADDR   = Output(UInt(XLEN.W))
  val ARLEN    = Output(UInt(8.W))
  val ARSIZE   = Output(UInt(3.W))
  val ARBURST  = Output(UInt(2.W))
  val ARLOCK   = Output(UInt(2.W))
  val ARCACHE  = Output(UInt(4.W))
  val ARPROT   = Output(UInt(3.W))
  val ARQOS    = Output(UInt(4.W))
  val ARREGION = Output(UInt(4.W))
  val ARUSER   = Output(UInt(1.W))
  val ARVALID  = Output(Bool())
  val ARREADY  = Input (Bool())
}

// Read data channel signals
trait AXIrd {
  val RID      = Input (UInt(4.W))
  val RDATA    = Input (UInt(XLEN.W))
  val RRESP    = Input (UInt(2.W))
  val RLAST    = Input (Bool())
  val RUSER    = Input (UInt(1.W))
  val RVALID   = Input (Bool())
  val RREADY   = Output(Bool())
}

// for simple single-direction communication
trait LastNext {
  val last = Input (Bool())
  val next = Output(Bool())
  val LVALID = Input(Bool())
  val LREADY = Output(Bool())
  val NVALID = Output(Bool())
  val NREADY = Input(Bool())
}

class AXI extends Bundle {
  // Global signal
  val ACLK     = Input (Clock())
  val ARESETn  = Input (Bool())
}
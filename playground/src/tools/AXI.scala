package tools

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._
import cpu.config._

// Write address channel signals
class AXIwa extends Bundle {
  val AWID     = Output(UInt(4.W))
  val AWADDR   = Output(UInt(XLEN.W))
  val AWLEN    = Output(UInt(8.W))
  val AWSIZE   = Output(UInt(3.W))
  val AWBURST  = Output(UInt(2.W))
  val AWLOCK   = Output(UInt(2.W))
  val AWCACHE  = Output(UInt(4.W))
  val AWPROT   = Output(UInt(3.W))
  val AWQOS    = Output(UInt(4.W))
  val AWREGION = Output(UInt(4.W))
  val AWUSER   = Output(UInt(1.W))
  val AWVALID  = Output(Bool())
  val AWREADY  = Input (Bool())
}

// Write data channel signals
class AXIwd extends Bundle {
  val WDATA    = Output(UInt(XLEN.W))
  val WSTRB    = Output(UInt((XLEN / 8).W))
  val WLAST    = Output(Bool())
  val WUSER    = Output(UInt(1.W))
  val WVALID   = Output(Bool())
  val WREADY   = Input (Bool())
}

// Write response channel signals
class AXIwr extends Bundle {
  val BID      = Input (UInt(4.W))
  val BRESP    = Input (UInt(2.W))
  val BUSER    = Input (UInt(1.W))
  val BVALID   = Input (Bool())
  val BREADY   = Output(Bool())
}

// Read address channel signals
class AXIra extends Bundle {
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
class AXIrd extends Bundle {
  val RID      = Input (UInt(4.W))
  val RDATA    = Input (UInt(XLEN.W))
  val RRESP    = Input (UInt(2.W))
  val RLAST    = Input (Bool())
  val RUSER    = Input (UInt(1.W))
  val RVALID   = Input (Bool())
  val RREADY   = Output(Bool())
}

// for simple single-direction communication
class LastVR extends Bundle {
  val VALID = Input(Bool())
  val READY = Output(Bool())
}

class BASIC extends Bundle {
  // Global signal
  val ACLK     = Input (Clock())
  val ARESETn  = Input (Bool())
}

class AxiMasterReadChannel extends Bundle {
  val axiRa = new AXIra
  val axiRd = new AXIrd
}

class AxiMasterChannel extends Bundle {
  val axiWa = new AXIwa
  val axiWd = new AXIwd
  val axiWr = new AXIwr
  val axiRa = new AXIra
  val axiRd = new AXIrd
}

class AxiSlaveChannel extends Bundle {
  val axiWa = Flipped(new AXIwa)
  val axiWd = Flipped(new AXIwd)
  val axiWr = Flipped(new AXIwr)
  val axiRa = Flipped(new AXIra)
  val axiRd = Flipped(new AXIrd)
}

class AxiSlaveIO extends AxiSlaveChannel {
  val basic = new BASIC
}

package tools

import chisel3._
import cpu.config.GeneralConfig._

// Write address channel signals
class AXIwa extends Bundle {
  val AWID     = Output(UInt(IDLEN.W))
  val AWADDR   = Output(UInt(ALEN.W))
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
  val BID      = Input (UInt(IDLEN.W))
  val BRESP    = Input (UInt(2.W))
  val BUSER    = Input (UInt(1.W))
  val BVALID   = Input (Bool())
  val BREADY   = Output(Bool())
}

// Read address channel signals
class AXIra extends Bundle {
  val ARID     = Output(UInt(IDLEN.W))
  val ARADDR   = Output(UInt(ALEN.W))
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
  val RID      = Input (UInt(IDLEN.W))
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

class AxiMasterChannel extends AxiMasterReadChannel {
  val axiWa = new AXIwa
  val axiWd = new AXIwd
  val axiWr = new AXIwr
}

class AxiSlaveChannel extends Bundle {
  val axiWa = Flipped(new AXIwa)
  val axiWd = Flipped(new AXIwd)
  val axiWr = Flipped(new AXIwr)
  val axiRa = Flipped(new AXIra)
  val axiRd = Flipped(new AXIrd)
}

class AxiSlaveIO extends Bundle {
  val basic   = new BASIC
  val channel = Flipped(new AxiMasterChannel)
}

class AxiSelectIO extends Bundle {
  val input   = Flipped(new AxiMasterChannel)
  val RamIO   = new AxiMasterChannel
  val MMIO    = new AxiMasterChannel
}

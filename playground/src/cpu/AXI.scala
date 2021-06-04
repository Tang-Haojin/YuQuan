package cpu.axi

import chisel3._
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
  val AWPORT   = Output(UInt(3.W))
  val AWQOS    = Output(UInt(4.W))
  val AWREGION = Output(UInt(4.W))
  val AWUSER   = Output(UInt(1.W))
  val AWVALID  = Output(Bool())
  val AWREADY  = Input (Bool())
}

// Write data channel signals
class AXIwd extends Bundle {
  val WID      = Output(UInt(0.W))
  val WDATA    = Output(UInt(XLEN.W))
  val WSTRB    = Output(UInt(4.W))
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

class AXIRaMux extends RawModule {
  val io = IO(new Bundle {
    val muxRaBasic     = new BASIC

    val muxAxiRaIn0    = Flipped(new AXIra)
    val muxAxiRaIn1    = Flipped(new AXIra)
    val muxAxiRaOut    = new AXIra
  })

  withClockAndReset(io.muxRaBasic.ACLK, ~io.muxRaBasic.ARESETn) {
    val selector = RegInit(0.B)

    io.muxAxiRaIn0.ARREADY := 0.B
    io.muxAxiRaIn1.ARREADY := 0.B

    when(selector) {
      io.muxAxiRaIn1 <> io.muxAxiRaOut
    }.otherwise {
      io.muxAxiRaIn0 <> io.muxAxiRaOut
    }

    when(~io.muxAxiRaOut.ARVALID || ~io.muxAxiRaOut.ARREADY) {
      selector := ~selector
    }
  }
}

class AXIRdMux extends RawModule {
  val io = IO(new Bundle {
    val muxRdBasic     = new BASIC

    val muxAxiRdIn0    = Flipped(new AXIrd)
    val muxAxiRdIn1    = Flipped(new AXIrd)
    val muxAxiRdOut    = new AXIrd
  })

  withClockAndReset(io.muxRdBasic.ACLK, ~io.muxRdBasic.ARESETn) {
    val selector = RegInit(0.B)

    io.muxAxiRdIn0.RID    := 0xf.U
    io.muxAxiRdIn1.RID    := 0xf.U
    io.muxAxiRdIn0.RDATA  := 0.U
    io.muxAxiRdIn1.RDATA  := 0.U
    io.muxAxiRdIn0.RRESP  := 0.U
    io.muxAxiRdIn1.RRESP  := 0.U
    io.muxAxiRdIn0.RLAST  := 0.B
    io.muxAxiRdIn1.RLAST  := 0.B
    io.muxAxiRdIn0.RUSER  := 0.U
    io.muxAxiRdIn1.RUSER  := 0.U
    io.muxAxiRdIn0.RVALID := 0.B
    io.muxAxiRdIn1.RVALID := 0.B

    when(selector) {
      io.muxAxiRdIn1 <> io.muxAxiRdOut
    }.otherwise {
      io.muxAxiRdIn0 <> io.muxAxiRdOut
    }

    when(~io.muxAxiRdOut.RVALID || ~io.muxAxiRdOut.RREADY || 
         (io.muxAxiRdOut.RID =/= selector.asUInt())) {
      selector := ~selector
    }
  }
}

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
  val AWPROT   = Output(UInt(3.W))
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

class AXIRaMux extends Module {
  val io = IO(new Bundle {
    val axiRaIn0    = Flipped(new AXIra)
    val axiRaIn1    = Flipped(new AXIra)
    val axiRaOut    = new AXIra
  })

  io.axiRaIn0.ARREADY := 0.B
  io.axiRaIn1.ARREADY := 0.B

  when(io.axiRaIn1.ARVALID && io.axiRaOut.ARREADY) {
    io.axiRaIn1 <> io.axiRaOut
  }.otherwise {
    io.axiRaIn0 <> io.axiRaOut
  }
}

class AXIRdMux extends Module {
  val io = IO(new Bundle {
    val axiRdIn0    = Flipped(new AXIrd)
    val axiRdIn1    = Flipped(new AXIrd)
    val axiRdOut    = new AXIrd
  })

  io.axiRdIn0.RID    := 0xf.U
  io.axiRdIn1.RID    := 0xf.U
  io.axiRdIn0.RDATA  := 0.U
  io.axiRdIn1.RDATA  := 0.U
  io.axiRdIn0.RRESP  := 0.U
  io.axiRdIn1.RRESP  := 0.U
  io.axiRdIn0.RLAST  := 0.B
  io.axiRdIn1.RLAST  := 0.B
  io.axiRdIn0.RUSER  := 0.U
  io.axiRdIn1.RUSER  := 0.U
  io.axiRdIn0.RVALID := 0.B
  io.axiRdIn1.RVALID := 0.B

  when(io.axiRdOut.RVALID && io.axiRdIn1.RREADY && io.axiRdOut.RID === 1.U) {
    io.axiRdIn1 <> io.axiRdOut
  }.otherwise {
    io.axiRdIn0 <> io.axiRdOut
  }
}

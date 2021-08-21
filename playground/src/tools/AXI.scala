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

class AxiMasterIO extends Bundle {
  val basic   = new BASIC
  val channel = new AxiMasterChannel
}

class AxiSelectIO extends Bundle {
  val input   = Flipped(new AxiMasterChannel)
  val RamIO   = new AxiMasterChannel
  val MMIO    = new AxiMasterChannel
}

/*
abstract class HandleAxiSlave(channel: AxiMasterChannel, baseAddr: Long = 0L) {
  val AWREADY = RegInit(1.B); channel.axiWa.AWREADY := AWREADY
  val WREADY  = RegInit(0.B); channel.axiWd.WREADY  := WREADY
  val BVALID  = RegInit(0.B); channel.axiWr.BVALID  := BVALID
  val ARREADY = RegInit(1.B); channel.axiRa.ARREADY := ARREADY
  val RVALID  = RegInit(0.B); channel.axiRd.RVALID  := RVALID
  val ARSIZE  = RegInit(0.U(3.W))
  val ARLEN   = RegInit(0.U(8.W))
  val AWSIZE  = RegInit(0.U(3.W))
  val AWLEN   = RegInit(0.U(8.W))

  val RID    = RegInit(0.U(IDLEN.W)); channel.axiRd.RID := RID
  val BID    = RegInit(0.U(IDLEN.W)); channel.axiWr.BID := BID
  val ARADDR = RegInit(0.U(ALEN.W))
  val AWADDR = RegInit(0.U(ALEN.W))

  val wireARADDR = WireDefault(UInt(ALEN.W), ARADDR)
  val wireRStep  = WireDefault(0.U(128.W))
  val wireWStep  = WireDefault(0.U(128.W))

  for (i <- 0 until 8) {
    when(ARSIZE === i.U) { wireRStep := (1 << i).U }
    when(AWSIZE === i.U) { wireWStep := (1 << i).U }
  }

  when(channel.axiRd.RVALID && channel.axiRd.RREADY) {
    when(ARLEN === 0.U) {
      RVALID         := 0.B
      ARREADY        := 1.B
      channel.axiRd.RLAST := 1.B
    }.otherwise {
      wireARADDR := ARADDR + wireRStep
      ARADDR     := wireARADDR
      ARLEN      := ARLEN - 1.U
    }
  }.elsewhen(channel.axiRa.ARVALID && channel.axiRa.ARREADY) {
    RID        := channel.axiRa.ARID
    wireARADDR := channel.axiRa.ARADDR(ALEN - 1, AxSIZE) ## 0.U(AxSIZE.W) - baseAddr.U
    ARADDR     := wireARADDR
    ARREADY    := 0.B
    RVALID     := 1.B
    ARSIZE     := channel.axiRa.ARSIZE
    ARLEN      := channel.axiRa.ARLEN
  }

  when(channel.axiWa.AWVALID && channel.axiWa.AWREADY) {
    AWADDR  := channel.axiWa.AWADDR(ALEN - 1, AxSIZE) ## 0.U(AxSIZE.W) - baseAddr.U
    BID     := channel.axiWa.AWID
    AWREADY := 0.B
    WREADY  := 1.B
    AWSIZE  := channel.axiWa.AWSIZE
    AWLEN   := channel.axiWa.AWLEN
  }

  when(channel.axiWd.WVALID && channel.axiWd.WREADY) {
    ram_write.io.wen := 1.B
    when(AWLEN === 0.U) {
      WREADY  := 0.B
      BVALID  := 1.B
    }.otherwise {
      AWADDR := AWADDR + wireWStep
      AWLEN  := AWLEN - 1.U
    }
  }

  when(channel.axiWr.BVALID && channel.axiWr.BREADY) {
    AWREADY := 1.B
    BVALID := 0.B
  }
}
*/
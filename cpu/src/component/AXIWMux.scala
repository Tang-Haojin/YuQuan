package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class AXIWMux(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val axiWaIn0 = Flipped(new AXIwa)
    val axiWaIn1 = Flipped(new AXIwa)
    val axiWaOut = new AXIwa
    val axiWdIn0 = Flipped(new AXIwd)
    val axiWdIn1 = Flipped(new AXIwd)
    val axiWdOut = new AXIwd
    val axiWrIn0 = Flipped(new AXIwr)
    val axiWrIn1 = Flipped(new AXIwr)
    val axiWrOut = new AXIwr
  })

  private val idle::busy::Nil = Enum(2)
  private val regState = RegInit(UInt(1.W), idle)
  private val state = WireDefault(UInt(1.W), regState); regState := state

  private val rrID = RegInit(0.U(1.W)); rrID := ~rrID // Round-Robin policy
  private val regCurrentID = RegInit(0.U(1.W))
  private val currentID = WireDefault(UInt(1.W), rrID)

  InitLinkIn (io.axiWaIn0, io.axiWdIn0, io.axiWrIn0)
  InitLinkIn (io.axiWaIn1, io.axiWdIn1, io.axiWrIn1)
  InitLinkOut(io.axiWaOut, io.axiWdOut, io.axiWrOut)

  when(regState === idle) {
    when(rrID === 0.U) {
      /* When VALID is asserted, it must remain asserted until the handshake occurs, 
       * so we need not to care about `AWREADY` or `WREADY` signal.
       * Factually, to avoid deadlock or combinational loop, we must not
       * consider the `READY` signal at all.
      */
      when(io.axiWaIn0.AWVALID || io.axiWdIn0.WVALID) {
        state        := busy
        regCurrentID := rrID
      }
    }
    when(rrID === 1.U) {
      when(io.axiWaIn1.AWVALID || io.axiWdIn1.WVALID) {
        state        := busy
        regCurrentID := rrID
      }
    }
  }
  when(regState === busy) {
    currentID := regCurrentID
  }
  when(state === busy) {
    when(currentID === 0.U) {
      io.axiWaIn0 <> io.axiWaOut
      io.axiWdIn0 <> io.axiWdOut
      io.axiWrIn0 <> io.axiWrOut
    }
    when(currentID === 1.U) {
      io.axiWaIn1 <> io.axiWaOut
      io.axiWdIn1 <> io.axiWdOut
      io.axiWrIn1 <> io.axiWrOut
    }
    when(io.axiWrOut.BREADY && io.axiWrOut.BVALID) {
      regState := idle
    }
  }
}

private class InitLinkIn(wa: AXIwa, wd: AXIwd, wr: AXIwr) {
  wa.AWREADY := 0.B
  wd.WREADY  := 0.B
  wr.BID     := 0xf.U
  wr.BVALID  := 0.B
  wr.BRESP   := 0.U
  wr.BUSER   := 0.U
}

private object InitLinkIn {
  def apply(wa: AXIwa, wd: AXIwd, wr: AXIwr): InitLinkIn = new InitLinkIn(wa, wd, wr)
}

private class InitLinkOut(wa: AXIwa, wd: AXIwd, wr: AXIwr) {
  wa.AWADDR   := 0.U
  wa.AWBURST  := 1.U
  wa.AWCACHE  := 0.U
  wa.AWID     := 0xf.U
  wa.AWLEN    := 0.U
  wa.AWLOCK   := 0.U
  wa.AWPROT   := 0.U
  wa.AWQOS    := 0.U
  wa.AWREGION := 0.U
  wa.AWSIZE   := 0.U
  wa.AWUSER   := 0.U
  wa.AWVALID  := 0.U
  wd.WDATA    := 0xfade.U
  wd.WLAST    := 0.B
  wd.WSTRB    := 0.U
  wd.WUSER    := 0.U
  wd.WVALID   := 0.B
  wr.BREADY   := 0.B
}

private object InitLinkOut {
  def apply(wa: AXIwa, wd: AXIwd, wr: AXIwr): InitLinkOut = new InitLinkOut(wa, wd, wr)
}

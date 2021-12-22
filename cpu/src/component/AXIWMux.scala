package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class AXIWMux(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val axiWaIn0 = Flipped(new AXI_BUNDLE_AW)
    val axiWaIn1 = Flipped(new AXI_BUNDLE_AW)
    val axiWaOut = new AXI_BUNDLE_AW
    val axiWdIn0 = Flipped(new AXI_BUNDLE_W)
    val axiWdIn1 = Flipped(new AXI_BUNDLE_W)
    val axiWdOut = new AXI_BUNDLE_W
    val axiWrIn0 = new AXI_BUNDLE_B
    val axiWrIn1 = new AXI_BUNDLE_B
    val axiWrOut = Flipped(new AXI_BUNDLE_B)
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
      when(io.axiWaIn0.valid || io.axiWdIn0.valid) {
        state        := busy
        regCurrentID := rrID
      }
    }
    when(rrID === 1.U) {
      when(io.axiWaIn1.valid || io.axiWdIn1.valid) {
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
    when(io.axiWrOut.fire) {
      regState := idle
    }
  }

  private class InitLinkIn(aw: AXI_BUNDLE_AW, w: AXI_BUNDLE_W, b: AXI_BUNDLE_B) {
    aw.ready    := 0.B
    w.ready     := 0.B
    b.bits.id   := 0xf.U
    b.valid     := 0.B
    b.bits.resp := 0.U
    b.bits.user := 0.U
  }

  private object InitLinkIn {
    def apply(aw: AXI_BUNDLE_AW, w: AXI_BUNDLE_W, b: AXI_BUNDLE_B): InitLinkIn = new InitLinkIn(aw, w, b)
  }

  private class InitLinkOut(aw: AXI_BUNDLE_AW, w: AXI_BUNDLE_W, b: AXI_BUNDLE_B) {
    aw.bits.addr   := 0.U
    aw.bits.burst  := 1.U
    aw.bits.cache  := 0.U
    aw.bits.id     := 0xf.U
    aw.bits.len    := 0.U
    aw.bits.lock   := 0.U
    aw.bits.prot   := 0.U
    aw.bits.qos    := 0.U
    aw.bits.region := 0.U
    aw.bits.size   := 0.U
    aw.bits.user   := 0.U
    aw.valid       := 0.U
    w.bits.data    := 0xfade.U
    w.bits.last    := 0.B
    w.bits.strb    := 0.U
    w.bits.user    := 0.U
    w.valid        := 0.B
    b.ready        := 0.B
  }

  private object InitLinkOut {
    def apply(aw: AXI_BUNDLE_AW, w: AXI_BUNDLE_W, b: AXI_BUNDLE_B): InitLinkOut = new InitLinkOut(aw, w, b)
  }
}
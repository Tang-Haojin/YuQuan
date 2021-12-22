package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class AXIRMux(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val axiRaIn0 = Flipped(new AXI_BUNDLE_AR)
    val axiRaIn1 = Flipped(new AXI_BUNDLE_AR)
    val axiRaOut = new AXI_BUNDLE_AR
    val axiRdIn0 = new AXI_BUNDLE_R
    val axiRdIn1 = new AXI_BUNDLE_R
    val axiRdOut = Flipped(new AXI_BUNDLE_R)
  })

  private val idle::busy::Nil = Enum(2)
  private val regState = RegInit(UInt(1.W), idle)
  private val state = WireDefault(UInt(1.W), regState); regState := state

  private val rrID = RegInit(0.U(1.W)); rrID := ~rrID // Round-Robin policy
  private val regCurrentID = RegInit(0.U(1.W))
  private val currentID = WireDefault(UInt(1.W), rrID)
  
  InitLinkIn (io.axiRaIn0, io.axiRdIn0)
  InitLinkIn (io.axiRaIn1, io.axiRdIn1)
  InitLinkOut(io.axiRaOut, io.axiRdOut)

  when(regState === idle) {
    when(rrID === 0.U) {
      when(io.axiRaIn0.valid) {
        state        := busy
        regCurrentID := rrID
      }
    }
    when(rrID === 1.U) {
      when(io.axiRaIn1.valid) {
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
      io.axiRaIn0 <> io.axiRaOut
      io.axiRdIn0 <> io.axiRdOut
    }
    when(currentID === 1.U) {
      io.axiRaIn1 <> io.axiRaOut
      io.axiRdIn1 <> io.axiRdOut
    }
    when(io.axiRdOut.fire && io.axiRdOut.bits.last) {
      regState := idle
    }
  }

  private class InitLinkIn(ar: AXI_BUNDLE_AR, r: AXI_BUNDLE_R) {
    ar.ready    := 0.B
    r.bits.id   := 0xf.U
    r.valid     := 0.B
    r.bits.resp := 0.U
    r.bits.user := 0.U
    r.bits.data := 0xfade.U
    r.bits.last := 0.B
  }

  private object InitLinkIn {
    def apply(ar: AXI_BUNDLE_AR, r: AXI_BUNDLE_R): InitLinkIn = new InitLinkIn(ar, r)
  }

  private class InitLinkOut(ar: AXI_BUNDLE_AR, r: AXI_BUNDLE_R) {
    ar.bits.addr   := 0.U
    ar.bits.burst  := 1.U
    ar.bits.cache  := 0.U
    ar.bits.id     := 0xf.U
    ar.bits.len    := 0.U
    ar.bits.lock   := 0.U
    ar.bits.prot   := 0.U
    ar.bits.qos    := 0.U
    ar.bits.region := 0.U
    ar.bits.size   := 0.U
    ar.bits.user   := 0.U
    ar.valid       := 0.U
    r.ready        := 0.B
  }

  private object InitLinkOut {
    def apply(ar: AXI_BUNDLE_AR, r: AXI_BUNDLE_R): InitLinkOut = new InitLinkOut(ar, r)
  }
}

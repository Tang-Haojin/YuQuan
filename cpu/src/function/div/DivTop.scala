package cpu.function.div

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class DivTop(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val input = Flipped(Decoupled(new Bundle {
      val dividend = UInt(xlen.W)
      val divisor  = UInt(xlen.W)
      val issigned = Bool()
    }))
    val output = Decoupled(new Bundle {
      val quotient  = UInt(xlen.W)
      val remainder = UInt(xlen.W)
    })
  })

  private val idle::preskip::skipping::busy::ending::Nil = Enum(5)
  private val state = RegInit(UInt(3.W), idle)

  private val  A = RegInit(0.U((2 * xlen + 1).W))
  private val hi = A(2 * xlen, xlen)
  private val lo = A(xlen - 1, 0)
  private val  d = RegInit(0.U(xlen.W))
  private val  n = RegInit(0.U(log2Ceil(xlen).W))
  private val dividendSign = RegInit(0.B)
  private val divisorSign  = RegInit(0.B)

  io.input.ready  := state === idle
  io.output.valid := state === ending
  io.output.bits.quotient  := Mux(dividendSign ^ divisorSign, -lo, lo)
  io.output.bits.remainder := Mux(dividendSign, -hi(xlen, 1), hi(xlen, 1))
  when(state === idle) {
    dividendSign := 0.B
    divisorSign  := 0.B
    A := io.input.bits.dividend ## 0.B
    d := io.input.bits.divisor
    when(io.input.bits.divisor === 0.U) { A := io.input.bits.dividend ## 0.B ## Fill(xlen, 1.B) }
    .elsewhen(io.input.bits.issigned) {
      dividendSign := io.input.bits.dividend(xlen - 1)
      divisorSign  := io.input.bits.divisor (xlen - 1)
      when(io.input.bits.dividend(xlen - 1)) { A := -io.input.bits.dividend ## 0.B }
      when(io.input.bits.divisor (xlen - 1)) { d := -io.input.bits.divisor }
    }
	  when(io.input.fire()) { state := Mux(io.input.bits.divisor === 0.U, ending, preskip) }
  }
  when(state === preskip) {
    val skip = (xlen.U | Log2(d)) - Log2(A(xlen, 0))
    n := Mux(skip > (xlen - 1).U, (xlen - 1).U, skip)
    state := Mux(skip === 0.U, busy, skipping)
  }
  when(state === skipping) { A := A << n; state := busy }
  when(state === busy) {
    A := Mux(hi >= d, hi(xlen - 1, 0) - d(xlen - 1, 0), hi(xlen - 1, 0)) ## lo ## (hi >= d)
    n := n + 1.U
    when(n === (xlen - 1).U) { state := ending }
  }
  when(state === ending) { when(io.output.fire()) { state := idle } }
}

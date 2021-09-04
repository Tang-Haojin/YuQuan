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

  private val idle::busy::ending::Nil = Enum(3)
  private val state = RegInit(UInt(2.W), idle)

  private val  A = RegInit(0.U((2 * xlen + 1).W))
  private val hi = A(2 * xlen, xlen)
  private val lo = A(xlen - 1, 0)
  private val  d = RegInit(0.U(xlen.W))
  private val  n = RegInit(0.U(log2Ceil(xlen).W))
  private val dividendSign = RegInit(0.B)
  private val divisorSign  = RegInit(0.B)
  private val rawQuotient  = lo
  private val rawRemainder = hi(xlen, 1)

  io.output.bits.quotient  := Mux(dividendSign ^ divisorSign, -rawQuotient, rawQuotient)
  io.output.bits.remainder := Mux(dividendSign, -rawRemainder, rawRemainder)

  io.input.ready  := 1.B
  io.output.valid := 0.B
  when(state === idle) {
	  when(io.input.fire) {
      dividendSign := 0.B
      divisorSign  := 0.B
      state := busy
      A     := io.input.bits.dividend ## 0.B
      d     := io.input.bits.divisor
      n     := (xlen - 1).U
      when(io.input.bits.divisor === 0.U) {
        A     := io.input.bits.dividend ## 0.B ## Fill(xlen, 1.B)
        state := ending
      }.elsewhen(io.input.bits.issigned) {
        dividendSign := io.input.bits.dividend(xlen - 1)
        divisorSign  := io.input.bits.divisor (xlen - 1)
        when(io.input.bits.dividend(xlen - 1)) { A := -io.input.bits.dividend ## 0.B }
        when(io.input.bits.divisor (xlen - 1)) { d := -io.input.bits.divisor }
        when((io.input.bits.dividend === (1.B ## 0.U((xlen - 1).W))) && (io.input.bits.divisor === Fill(xlen, 1.B))) {
          dividendSign := 0.B
          divisorSign  := 0.B
          A := 1.B ## 0.U((xlen - 1).W)
          state := ending
        }
      }
    }
  }
  when(state === busy) {
    io.input.ready := 0.B
    A := Mux(hi >= d, hi - d, hi)(xlen - 1, 0) ## lo ## (hi >= d)
    n := n - 1.U
    when(n === 0.U) { state := ending }
  }
  when(state === ending) {
    io.input.ready  := 0.B
    io.output.valid := 1.B
    when(io.output.fire) { state := idle }
  }
}

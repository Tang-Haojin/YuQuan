package cpu.component

import chisel3._
import chisel3.util._

import Operators._
import chipsalliance.rocketchip.config._
import cpu.tools._
import cpu.function.mul._
import cpu.function.div._

class ALU(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val input = Flipped(Decoupled(new YQBundle {
      val op   = UInt(AluTypeWidth.W)
      val a    = SInt(xlen.W)
      val b    = SInt(xlen.W)
      val word = Bool()
      val sign = UInt(2.W)
    }))
    val output = Decoupled(SInt(xlen.W))
  })
  private val a = io.input.bits.a
  private val b = io.input.bits.b

  private val result = WireDefault(SInt(xlen.W), io.input.bits.a)
  io.output.bits := result

  private val isMul = (io.input.bits.op === mul) || (io.input.bits.op === mulh)
  private val multiTop = Module(new MultiTop)
  multiTop.io.input.bits.data(0) := a.asUInt
  multiTop.io.input.bits.data(1) := b.asUInt
  multiTop.io.input.bits.sign(0) := io.input.bits.sign(0)
  multiTop.io.input.bits.sign(1) := io.input.bits.sign(1)
  multiTop.io.input.valid  := isMul && io.input.valid
  multiTop.io.output.ready := io.output.ready

  private val isDiv = (io.input.bits.op === div) || (io.input.bits.op === divw) || (io.input.bits.op === divu) || (io.input.bits.op === duw)
  private val isRem = (io.input.bits.op === rem) || (io.input.bits.op === remw) || (io.input.bits.op === remu) || (io.input.bits.op === ruw)
  private val isSgn = (io.input.bits.op === rem) || (io.input.bits.op === remw) || (io.input.bits.op === divw) || (io.input.bits.op === div)
  private val divTop = Module(new DivTop)
  divTop.io.input.bits.dividend := a.asUInt
  divTop.io.input.bits.divisor  := b.asUInt
  if (xlen == 64) when(io.input.bits.word) {
    divTop.io.input.bits.dividend := Fill(xlen - 32, (a(31) & isSgn)) ## a(31, 0)
    divTop.io.input.bits.divisor  := Fill(xlen - 32, (b(31) & isSgn)) ## b(31, 0)
  }
  divTop.io.input.bits.issigned := isSgn
  divTop.io.input.valid  := (isDiv || isRem) && io.input.valid
  divTop.io.output.ready := io.output.ready

  io.input.ready  := multiTop.io.input.ready && divTop.io.input.ready
  io.output.valid := 1.B

  when(multiTop.io.input.fire || divTop.io.input.fire) { io.output.valid := 0.B }
  when(!multiTop.io.input.ready) { io.output.valid := multiTop.io.output.valid }
  when(!divTop.io.input.ready) { io.output.valid := divTop.io.output.valid }

  private val shiftness = WireDefault(UInt(6.W), if (xlen == 64) b(5, 0) else b(4, 0)); when(io.input.bits.word) { shiftness := b(4, 0) }
  private val sl = WireDefault(UInt(xlen.W), VecInit(Seq.tabulate(xlen)(x => if (x == 0) a.asUInt else a(xlen - x - 1, 0) ## 0.U(x.W)))(shiftness))

  switch(io.input.bits.op) {
    is(add)  { result := a + b }
    is(sub)  { result := a - b }
    is(and)  { result := a & b }
    is(or)   { result := a | b }
    is(xor)  { result := a ^ b }
    is(sll)  { result := sl.asSInt }
    is(sra)  { result := a >> (if (xlen == 64) b(5, 0) else b(4, 0)) }
    is(srl)  { result := (a.asUInt >> (if (xlen == 64) b(5, 0) else b(4, 0))).asSInt }
    is(lts)  { result := Cat(Fill(xlen - 1, 0.U), a < b).asSInt }
    is(ltu)  { result := Cat(Fill(xlen - 1, 0.U), a.asUInt < b.asUInt).asSInt }
    is(equ)  { result := Cat(Fill(xlen - 1, 0.U), a === b).asSInt }
    is(neq)  { result := Cat(Fill(xlen - 1, 0.U), a =/= b).asSInt }
    is(ges)  { result := Cat(Fill(xlen - 1, 0.U), a >= b).asSInt }
    is(geu)  { result := Cat(Fill(xlen - 1, 0.U), a.asUInt >= b.asUInt).asSInt }
    is(mul)  { result := multiTop.io.output.bits(xlen - 1, 0).asSInt }
    is(rem)  { result := divTop.io.output.bits.remainder.asSInt }
    is(div)  { result := divTop.io.output.bits.quotient.asSInt }
    is(remu) { result := divTop.io.output.bits.remainder.asSInt }
    is(divu) { result := divTop.io.output.bits.quotient.asSInt }
    is(mulh) { result := multiTop.io.output.bits(2 * xlen - 1, xlen).asSInt }
  }

  if (xlen == 64) {
    switch(io.input.bits.op) {
      is(sllw) { result := sl(31, 0).asSInt }
      is(srlw) { result := (Cat(Fill(xlen - 32, 0.U), a(31, 0)) >> b(4, 0)).asSInt }
      is(sraw) { result := (Cat(Fill(xlen - 32, a(31)), a(31, 0)) >> b(4, 0)).asSInt }
      is(divw) { result := divTop.io.output.bits.quotient(31, 0).asSInt }
      is(remw) { result := divTop.io.output.bits.remainder(31, 0).asSInt }
      is(duw)  { result := divTop.io.output.bits.quotient(31, 0).asSInt }
      is(ruw)  { result := divTop.io.output.bits.remainder(31, 0).asSInt }
    }
  }

  when(io.input.bits.word) { io.output.bits := (Fill(32, result(31)) ## result(31, 0)).asSInt }
}

class SimpleALU(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val op  = Input (UInt(AluTypeWidth.W))
    val a   = Input (SInt(xlen.W))
    val b   = Input (SInt(xlen.W))
    val res = Output(SInt(xlen.W))
  })
  io.res := io.a
  switch(io.op) {
    is(lts)  { io.res := Cat(Fill(xlen - 1, 0.U), io.a < io.b).asSInt }
    is(ltu)  { io.res := Cat(Fill(xlen - 1, 0.U), io.a.asUInt < io.b.asUInt).asSInt }
    is(equ)  { io.res := Cat(Fill(xlen - 1, 0.U), io.a === io.b).asSInt }
    is(neq)  { io.res := Cat(Fill(xlen - 1, 0.U), io.a =/= io.b).asSInt }
    is(ges)  { io.res := Cat(Fill(xlen - 1, 0.U), io.a >= io.b).asSInt }
    is(geu)  { io.res := Cat(Fill(xlen - 1, 0.U), io.a.asUInt >= io.b.asUInt).asSInt }
  }
}

object Operators {
  var operators = Enum(28)
  val err::add::sub::and::or::xor::sll::sra::Nil = operators.take(8)
  operators = operators.drop(8)
  val srl::lts::ltu::equ::neq::sllw::srlw::sraw::Nil = operators.take(8)
  operators = operators.drop(8)
  val ges::geu::mul::divw::remw::rem::div::remu::Nil = operators.take(8)
  operators = operators.drop(8)
  val divu::mulh::duw::ruw::Nil = operators
}

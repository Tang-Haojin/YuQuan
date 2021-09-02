package cpu.component

import chisel3._
import chisel3.util._

import Operators._
import chipsalliance.rocketchip.config._
import cpu.tools._
import cpu.function.mul._

class ALU(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val input = Flipped(Decoupled(new YQBundle {
      val op  = Input (UInt(AluTypeWidth.W))
      val a   = Input (SInt(xlen.W))
      val b   = Input (SInt(xlen.W))
    }))
    val output = Decoupled(SInt(xlen.W))
  })
  private val a = io.input.bits.a
  private val b = io.input.bits.b

  io.output.bits := io.input.bits.a

  private val isMul = (io.input.bits.op === mul) || (io.input.bits.op === mulh)

  private val multiTop = Module(new MultiTop)
  multiTop.io.input.bits.data(0) := a.asUInt
  multiTop.io.input.bits.data(1) := b.asUInt
  multiTop.io.input.bits.sign(0) := 1.B
  multiTop.io.input.bits.sign(1) := 1.B
  multiTop.io.input.valid  := isMul && io.input.valid
  multiTop.io.output.ready := io.output.ready

  io.input.ready  := multiTop.io.input.ready
  io.output.valid := 1.B

  when(multiTop.io.input.fire) {
    io.output.valid := 0.B
  }
  when(!multiTop.io.input.ready) {
    io.output.valid := multiTop.io.output.valid
  }
    
  switch(io.input.bits.op) {
    is(add)  { io.output.bits := a + b }
    is(sub)  { io.output.bits := a - b }
    is(and)  { io.output.bits := a & b }
    is(or)   { io.output.bits := a | b }
    is(xor)  { io.output.bits := a ^ b }
    is(sll)  { io.output.bits := a << (if (xlen == 64) b(5, 0) else b(4, 0)).asUInt }
    is(sra)  { io.output.bits := a >> (if (xlen == 64) b(5, 0) else b(4, 0)).asUInt }
    is(srl)  { io.output.bits := (a.asUInt >> (if (xlen == 64) b(5, 0) else b(4, 0)).asUInt).asSInt }
    is(lts)  { io.output.bits := Cat(Fill(xlen - 1, 0.U), a < b).asSInt }
    is(ltu)  { io.output.bits := Cat(Fill(xlen - 1, 0.U), a.asUInt < b.asUInt).asSInt }
    is(equ)  { io.output.bits := Cat(Fill(xlen - 1, 0.U), a === b).asSInt }
    is(neq)  { io.output.bits := Cat(Fill(xlen - 1, 0.U), a =/= b).asSInt }
    is(ges)  { io.output.bits := Cat(Fill(xlen - 1, 0.U), a >= b).asSInt }
    is(geu)  { io.output.bits := Cat(Fill(xlen - 1, 0.U), a.asUInt >= b.asUInt).asSInt }
    is(mul)  { io.output.bits := multiTop.io.output.bits.asSInt }
    is(rem)  { io.output.bits := a - b * (a / b) }
    is(div)  { io.output.bits := a / b }
    is(remu) { io.output.bits := (a.asUInt % b.asUInt).asSInt }
    is(divu) { io.output.bits := (a.asUInt / b.asUInt).asSInt }
    is(mulh) { io.output.bits := multiTop.io.output.bits.asSInt >> xlen.U }
    is(duw)  { io.output.bits := (a(31, 0) / b(31, 0)).asSInt }
    is(ruw)  { io.output.bits := (a(31, 0) / b(31, 0)).asSInt }
  }

  if (xlen == 64) {
    switch(io.input.bits.op) {
      is(sllw) { io.output.bits := (a << b(4, 0).asUInt)(31, 0).asSInt }
      is(srlw) { io.output.bits := (Cat(Fill(xlen - 32, 0.U), a(31, 0)) >> b(4, 0).asUInt).asSInt }
      is(sraw) { io.output.bits := (Cat(Fill(xlen - 32, a(31)), a(31, 0)) >> b(4, 0).asUInt).asSInt }
      is(divw) { io.output.bits := a(31, 0).asSInt / b(31, 0).asSInt }
      is(remw) { io.output.bits := a(31, 0).asSInt - b(31, 0).asSInt * (a(31, 0).asSInt / b(31, 0).asSInt) }
    }
  }
}

class SimpleALU(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val op  = Input (UInt(AluTypeWidth.W))
    val a   = Input (SInt(xlen.W))
    val b   = Input (SInt(xlen.W))
    val res = Output(SInt(xlen.W))
  })
  val a = io.a
  val b = io.b

  io.res := io.a
    
  switch(io.op) {
    is(lts)  { io.res := Cat(Fill(xlen - 1, 0.U), a < b).asSInt }
    is(ltu)  { io.res := Cat(Fill(xlen - 1, 0.U), a.asUInt < b.asUInt).asSInt }
    is(equ)  { io.res := Cat(Fill(xlen - 1, 0.U), a === b).asSInt }
    is(neq)  { io.res := Cat(Fill(xlen - 1, 0.U), a =/= b).asSInt }
    is(ges)  { io.res := Cat(Fill(xlen - 1, 0.U), a >= b).asSInt }
    is(geu)  { io.res := Cat(Fill(xlen - 1, 0.U), a.asUInt >= b.asUInt).asSInt }
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

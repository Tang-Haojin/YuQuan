package cpu.component

import chisel3._
import chisel3.util._

import Operators._
import chipsalliance.rocketchip.config._
import cpu.tools._

class ALU(implicit p: Parameters) extends YQModule {
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
    is(add)  { io.res := a + b }
    is(sub)  { io.res := a - b }
    is(and)  { io.res := a & b }
    is(or)   { io.res := a | b }
    is(xor)  { io.res := a ^ b }
    is(sll)  { io.res := a << (if (xlen == 64) b(5, 0) else b(4, 0)).asUInt }
    is(sra)  { io.res := a >> (if (xlen == 64) b(5, 0) else b(4, 0)).asUInt }
    is(srl)  { io.res := (a.asUInt >> (if (xlen == 64) b(5, 0) else b(4, 0)).asUInt).asSInt }
    is(lts)  { io.res := Cat(Fill(xlen - 1, 0.U), a < b).asSInt }
    is(ltu)  { io.res := Cat(Fill(xlen - 1, 0.U), a.asUInt < b.asUInt).asSInt }
    is(equ)  { io.res := Cat(Fill(xlen - 1, 0.U), a === b).asSInt }
    is(neq)  { io.res := Cat(Fill(xlen - 1, 0.U), a =/= b).asSInt }
    is(ges)  { io.res := Cat(Fill(xlen - 1, 0.U), a >= b).asSInt }
    is(geu)  { io.res := Cat(Fill(xlen - 1, 0.U), a.asUInt >= b.asUInt).asSInt }
    is(mul)  { io.res := a * b }
    is(rem)  { io.res := a - b * (a / b) }
    is(div)  { io.res := a / b }
    is(remu) { io.res := (a.asUInt % b.asUInt).asSInt }
    is(divu) { io.res := (a.asUInt / b.asUInt).asSInt }
    is(mulh) { io.res := (a * b) >> xlen.U }
    is(duw)  { io.res := (a(31, 0) / b(31, 0)).asSInt }
    is(ruw)  { io.res := (a(31, 0) / b(31, 0)).asSInt }
  }
  
  if (xlen == 64) {
    switch(io.op) {
      is(sllw) { io.res := (a << b(4, 0).asUInt)(31, 0).asSInt }
      is(srlw) { io.res := (Cat(Fill(xlen - 32, 0.U), a(31, 0)) >> b(4, 0).asUInt).asSInt }
      is(sraw) { io.res := (Cat(Fill(xlen - 32, a(31)), a(31, 0)) >> b(4, 0).asUInt).asSInt }
      is(divw) { io.res := a(31, 0).asSInt / b(31, 0).asSInt }
      is(remw) { io.res := a(31, 0).asSInt - b(31, 0).asSInt * (a(31, 0).asSInt / b(31, 0).asSInt) }
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

package cpu

import chisel3._
import chisel3.util._

import cpu.Operators._
import cpu.config.GeneralConfig._

object Operators {
  val operators = Enum(14)
  val err::add::sub::and::or::xor::sll::sra::srl::lts::ltu::equ::neq::sllw::Nil = operators
}

class ALU extends Module {
  val io = IO(new Bundle {
    val op     = Input (UInt(AluTypeWidth.W))
    val a      = Input (SInt(XLEN.W))
    val b      = Input (SInt(XLEN.W))
    val res    = Output(SInt(XLEN.W))
  })
  val op  = io.op
  val a   = io.a
  val b   = io.b
  val res = io.res

  res := a
    
  switch(op) {
    is(add)  { res := a + b }
    is(sub)  { res := a - b }
    is(and)  { res := a & b }
    is(or)   { res := a | b }
    is(xor)  { res := a ^ b }
    is(sll)  { res := a << b(5, 0).asUInt }
    is(sra)  { res := a >> b(5, 0).asUInt }
    is(srl)  { res := (a.asUInt >> b(5, 0).asUInt).asSInt }
    is(lts)  { res := Cat(Fill(XLEN - 1, 0.U), (a < b)).asSInt }
    is(ltu)  { res := Cat(Fill(XLEN - 1, 0.U), (a.asUInt < b.asUInt)).asSInt }
    is(equ)  { res := Cat(Fill(XLEN - 1, 0.U), (a === b)).asSInt }
    is(neq)  { res := Cat(Fill(XLEN - 1, 0.U), (a =/= b)).asSInt }
    is(sllw) { res := a << b(4, 0).asUInt }
  }
}
package cpu

import chisel3._
import chisel3.util._

import cpu.Operators._
import cpu.config.GeneralConfig._

object Operators {
  val operators = Enum(10)
  val add::sub::and::or::xor::sll::sra::srl::lts::ltu::Nil = operators
}

class ALU extends Module {
  val io = IO(new Bundle {
    val op     = Input (UInt(AluTypeWidth.W)) // for extension ability
    val a      = Input (SInt(XLEN.W))
    val b      = Input (SInt(XLEN.W))
    val res    = Output(SInt(XLEN.W))
  })
  val op  = io.op
  val a   = io.a
  val b   = io.b
  val res = io.res

  res := 0.S
    
  switch(op) {
    is(add) { res := a + b }
    is(sub) { res := a - b }
    is(and) { res := a & b }
    is(or)  { res := a | b }
    is(xor) { res := a ^ b }
    is(sll) { res := a << b(2, 0).asUInt }
    is(sra) { res := a >> b(2, 0).asUInt }
    is(srl) { res := (a.asUInt >> b(2, 0).asUInt).asSInt }
    is(lts) { res := (a < b).asSInt }
    is(ltu) { res := (a.asUInt < b.asUInt).asSInt }
  }
}
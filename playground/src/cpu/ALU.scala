package cpu

import chisel3._
import chisel3.util._

import cpu.Operators._

object Operators {
  val operators = Enum(8)
  val add::sub::and::or::xor::sll::sra::srl::Nil = operators
}

class ALU(bitwidth: Int) extends Module {
  val io = IO(new Bundle {
    val op     = Input (UInt(4.W)) // for extension ability
    val a      = Input (SInt(bitwidth.W))
    val b      = Input (SInt(bitwidth.W))
    val res    = Output(SInt(bitwidth.W))
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
  }
}
package function.mul

import chisel3._
import chisel3.util._

class CLA_4 extends RawModule {
  val io = IO(new Bundle {
    val op1  = Input (UInt(4.W))
    val op2  = Input (UInt(4.W))
    val cin  = Input (Bool())
    val sum  = Output(UInt(4.W))
    val cout = Output(Bool())
  })
  private val g = Seq.tabulate(4)(x => io.op1(x) & io.op2(x))
  private val p = Seq.tabulate(4)(x => io.op1(x) ^ io.op2(x))
  private val c:  Seq[Bool] = Seq(
    io.cin,
    g(0) ^ (c(0) & p(0)),
    g(1) ^ ((g(0) ^ (c(0) & p(0))) & p(1)),
    g(2) ^ ((g(1) ^ ((g(0) ^ (c(0) & p(0))) & p(1))) & p(2))
  )
  io.sum  := (p(3) ## p(2) ## p(1) ## p(0)) ^ (c(3) ## c(2) ## c(1) ## c(0))
  io.cout := g(3) ^ ((g(2) ^ ((g(1) ^ ((g(0) ^ (c(0) & p(0))) & p(1))) & p(2))) & p(3))
}

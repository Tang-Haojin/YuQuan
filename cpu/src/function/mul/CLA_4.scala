package cpu.function.mul

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class CLA_4(implicit p: Parameters) extends YQRawModule {
  val io = IO(new Bundle {
    val op1  = Input (UInt(4.W))
    val op2  = Input (UInt(4.W))
    val cin  = Input (Bool())
    val sum  = Output(UInt(4.W))
    val cout = Output(Bool())
  })
  private val G = Seq.tabulate(4)(x => io.op1(x) & io.op2(x))
  private val P = Seq.tabulate(4)(x => io.op1(x) ^ io.op2(x))
  private val C:  Seq[Bool] = Seq(
    io.cin,
    G(0) ^ (io.cin & P(0)),
    G(1) ^ ((G(0) ^ (io.cin & P(0))) & P(1)),
    G(2) ^ ((G(1) ^ ((G(0) ^ (io.cin & P(0))) & P(1))) & P(2))
  )
  io.sum  := (P(3) ## P(2) ## P(1) ## P(0)) ^ (C(3) ## C(2) ## C(1) ## C(0))
  io.cout := G(3) ^ ((G(2) ^ ((G(1) ^ ((G(0) ^ (C(0) & P(0))) & P(1))) & P(2))) & P(3))
}

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

class CLA_4x24(implicit p: Parameters) {
  private val outVec = WireDefault(VecInit(Seq.fill(24)(0.U(4.W))))
  val input  = Seq.fill(2)(WireDefault(0.U(96.W)))
  val output = outVec.asUInt
  private val CLAs = Seq.tabulate(24)(x => {
    val y = Module(new CLA_4)
    y.io.op1  := input(0)(4 * x + 3, 4 * x)
    y.io.op2  := input(1)(4 * x + 3, 4 * x)
    outVec(x) := y.io.sum
    y
  })
  CLAs(0).io.cin := 0.B
  for (i <- 1 until CLAs.length) CLAs(i).io.cin := CLAs(i - 1).io.cout
}

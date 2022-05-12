package cpu.function.mul

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class MulTop(implicit p: Parameters) extends YQModule {
  val io = IO(new MulTopIO)

  private val isFree = RegInit(1.B)
  private val stage  = RegInit(0.U(2.W))

  private val data_in = Reg(Vec(2, UInt(xlen.W)))
  private val sign_in = Reg(Vec(2, Bool()))

  private val data = WireDefault(Vec(2, UInt(xlen.W)), io.input.bits.data)
  private val sign = WireDefault(Vec(2, Bool()), io.input.bits.sign)

  private val op_0 = data(0)
  private val op_1 = Fill(2, (sign(1) & data(1)(xlen - 1))) ## data(1)

  private val boothSext = Module(new BoothSext(17, xlen))
  private val walTree = Module(new WalImproved)
  boothSext.io.sign := sign(0)
  boothSext.io.op_0 := data(0)
  boothSext.io.input(0)   := op_1(1, 0) ## 0.B
  walTree.io.input(0) := boothSext.io.output(0) >> 2
  for (i <- 1 until 17) {
    boothSext.io.input(i)   := op_1(2 * i + 1, 2 * i - 1)
    walTree.io.input(i) := boothSext.io.output(i) << 2 * (i - 1)
  }
  walTree.io.input(17) := 1.B ## op_1(xlen + 1) ## {
    if (xlen == 64) 0.U(31.W) ## op_1(33) ## 0.U(32.W)
    else            0.U(32.W)
  }

  private val part_sum = Reg(Vec(2, UInt((2 * xlen).W)))

  private val out_valid = RegInit(0.B)

  io.output.valid := out_valid
  io.input.ready  := isFree

  if (xlen == 64) {
    val lo_34    = Reg(UInt(34.W))
    val lo_34_in = Reg(Bool())
    val hi_94    = Reg(UInt(94.W))
    io.output.bits  := hi_94 ## lo_34
    val res_0 = walTree.io.output(0)(33, 0) +& walTree.io.output(1)(33, 0)
    when(stage === 0.U) {
      data_in  := io.input.bits.data
      sign_in  := io.input.bits.sign
      lo_34    := res_0(33, 0)
      lo_34_in := res_0(34)
      part_sum(0) := walTree.io.output(0)(108, 34) ## 0.U(34.W)
      part_sum(1) := walTree.io.output(1)(108, 34) ## 0.U(34.W)
      when(io.input.fire) {
        isFree := 0.B
        stage  := 1.U
      }
    }

    when(stage === 1.U) {
      data  := data_in
      sign  := sign_in
      stage := 2.U
      for (i <- 0 until 16) {
        walTree.io.input(i)   := boothSext.io.output(i)((boothSext.io.output(i).getWidth - 1) min (128 - (32 + i * 2) - 1), 0) << 32 + i * 2
        boothSext.io.input(i) := op_1(2 * (i + 17) + 1, 2 * (i + 17) - 1)
      }
      walTree.io.input( 2) := boothSext.io.output(2) ## 0.B ## lo_34_in ## 0.U(34.W)
      walTree.io.input(16) := part_sum(0)
      walTree.io.input(17) := part_sum(1)
      part_sum(0) := walTree.io.output(0)
      part_sum(1) := walTree.io.output(1)
    }

    when(stage === 2.U) {
      val res = part_sum(0)(127, 34) + part_sum(1)(127, 34)
      stage := 0.U
      hi_94 := res
      io.output.bits := res ## lo_34
      io.output.valid := 1.B
      out_valid := 1.B
    }
  } else if (xlen == 32) {
    io.output.bits := part_sum(0) + part_sum(1)
    when(stage === 0.U) {
      part_sum := walTree.io.output
      when(io.input.fire) {
        isFree := 0.B
        stage  := 1.U
      }
    }
    when(stage === 1.U) {
      stage := 0.U
      io.output.valid := 1.B
      out_valid := 1.B
    }
  }

  when(io.output.fire) {
    out_valid := 0.B
    isFree    := 1.B
  }
}

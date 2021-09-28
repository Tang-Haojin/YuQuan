package cpu.function.mul

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class MultiTop(implicit p: Parameters) extends YQModule {
  val io = IO(new MultiTopIO)

  private val isFree = RegInit(1.B)
  private val stage  = RegInit(0.U(2.W))

  private val data_in = RegInit(VecInit(Seq.fill(2)(0.U(64.W))))
  private val sign_in = RegInit(VecInit(Seq.fill(2)(0.B)))

  private val data = WireDefault(Vec(2, UInt(64.W)), io.input.bits.data)
  private val sign = WireDefault(Vec(2, Bool()), io.input.bits.sign)

  private val op_0 = data(0)
  private val op_1 = Fill(2, (sign(1) & data(1)(63))) ## data(1)

  private val lo_34    = RegInit(0.U(34.W))
  private val lo_34_in = RegInit(0.B)
  private val hi_94    = RegInit(0.U(94.W))

  private val boothSext = Module(new BoothSext(17, 64))
  private val wallaceTree = Module(new Wallace_Improved(128))
  boothSext.io.sign := sign(0)
  boothSext.io.op_0 := data(0)
  boothSext.io.input(0)   := op_1(1, 0) ## 0.B
  wallaceTree.io.input(0) := boothSext.io.output(0) >> 2
  for (i <- 1 until 17) {
    boothSext.io.input(i)   := op_1(2 * i + 1, 2 * i - 1)
    wallaceTree.io.input(i) := boothSext.io.output(i) << 2 * (i - 1)
  }
  wallaceTree.io.input(17) := 1.B ## op_1(65) ## 0.U(31.W) ## op_1(33) ## 0.U(32.W)

  private val part_sum = RegInit(VecInit(Seq.fill(2)(0.U(128.W))))

  private val out_valid = RegInit(0.B)

  io.output.bits  := hi_94 ## lo_34
  io.output.valid := out_valid
  io.input.ready  := isFree

  when(io.input.fire) {
    val res = wallaceTree.io.output(0)(33, 0) +& wallaceTree.io.output(1)(33, 0)
    isFree   := 0.B
    stage    := 1.U
    data_in  := io.input.bits.data
    sign_in  := io.input.bits.sign
    lo_34    := res(33, 0)
    lo_34_in := res(34)
    part_sum(0) := wallaceTree.io.output(0)(108, 34) ## 0.U(34.W)
    part_sum(1) := wallaceTree.io.output(1)(108, 34) ## 0.U(34.W)
  }

  when(stage === 1.U) {
    data  := data_in
    sign  := sign_in
    stage := 2.U
    for (i <- 0 until 16) {
      wallaceTree.io.input(i) := boothSext.io.output(i)((boothSext.io.output(i).getWidth - 1) min (128 - (32 + i * 2) - 1), 0) << 32 + i * 2
      boothSext.io.input(i)   := op_1(2 * (i + 17) + 1, 2 * (i + 17) - 1)
    }
    wallaceTree.io.input( 2) := boothSext.io.output(2) ## 0.B ## lo_34_in ## 0.U(34.W)
    wallaceTree.io.input(16) := part_sum(0)
    wallaceTree.io.input(17) := part_sum(1)
    part_sum(0) := wallaceTree.io.output(0)
    part_sum(1) := wallaceTree.io.output(1)
  }

  when(stage === 2.U) {
    val res = part_sum(0)(127, 34) + part_sum(1)(127, 34)
    stage := 0.U
    hi_94 := res
    io.output.bits := res ## lo_34
    io.output.valid := 1.B
    out_valid := 1.B
  }

  when(io.output.fire()) {
    out_valid := 0.B
    isFree    := 1.B
  }
}

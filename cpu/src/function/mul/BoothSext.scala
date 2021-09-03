package cpu.function.mul

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class BoothSext(entries: Int = 17, size: Int = 64)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new BoothSextIO(entries, size))
  private val op_sign = io.op_0(size - 1) & io.sign
  for (i <- io.output.indices) {
    val last = if (i == 0) 0.B else io.input(i - 1)(2)
    val main_num = MuxLookup(io.input(i), 0.U((size + 1).W), Seq(
      "b001".U ->   op_sign ## io.op_0,
      "b010".U ->   op_sign ## io.op_0,
      "b011".U ->   io.op_0 ## 0.B,
      "b100".U -> ~(io.op_0 ## 0.B),
      "b101".U -> ~(op_sign ## io.op_0),
      "b110".U -> ~(op_sign ## io.op_0),
      "b111".U ->  Fill(size + 1, 1.B)
    ))
    val ext_sign = (io.sign && ~main_num(size)) || (~io.sign && ~io.input(i)(2))
    io.output(i) := 1.B ## ext_sign ## main_num ## 0.B ## last
  }
}

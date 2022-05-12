package cpu.function.mul

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class CSA(implicit p: Parameters) extends YQRawModule {
  val io = IO(new CSAIO)
  io.output(0) := io.input(0) ^ io.input(1) ^ io.input(2)
  io.output(1) := ((io.input(0)(2 * xlen - 2, 0) & io.input(1)(2 * xlen - 2, 0)) |
                   (io.input(1)(2 * xlen - 2, 0) & io.input(2)(2 * xlen - 2, 0)) |
                   (io.input(2)(2 * xlen - 2, 0) & io.input(0)(2 * xlen - 2, 0))) ## 0.B
}

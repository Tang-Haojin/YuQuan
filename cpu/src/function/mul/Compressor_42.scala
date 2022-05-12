package cpu.function.mul

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class Compressor_42(implicit p: Parameters) extends YQRawModule {
  val io = IO(new _42_CompressorIO)
  private val w1 = io.input(0) ^ io.input(1) ^ io.input(2) ^ io.input(3)
  private val w2 = (io.input(0)(2 * xlen - 2, 0) & io.input(1)(2 * xlen - 2, 0)) | (io.input(2)(2 * xlen - 2, 0) & io.input(3)(2 * xlen - 2, 0))
  private val w3 = (io.input(0)(2 * xlen - 2, 0) | io.input(1)(2 * xlen - 2, 0)) & (io.input(2)(2 * xlen - 2, 0) | io.input(3)(2 * xlen - 2, 0))
  io.output(0) := ((w1(2 * xlen - 2, 0) & (w3(2 * xlen - 3, 0) ## 0.B)) | (~w1(2 * xlen - 2, 0) & w2)) ## 0.B
  io.output(1) := w1 ^ (w3 ## 0.B)
}

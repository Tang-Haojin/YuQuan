package cpu.function.mul

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class Compressor_42(length: Int = 128)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new _42_CompressorIO(length))
  private val w1 = io.input(0) ^ io.input(1) ^ io.input(2) ^ io.input(3)
  private val w2 = (io.input(0)(length - 2, 0) & io.input(1)(length - 2, 0)) | (io.input(2)(length - 2, 0) & io.input(3)(length - 2, 0))
  private val w3 = (io.input(0)(length - 2, 0) | io.input(1)(length - 2, 0)) & (io.input(2)(length - 2, 0) | io.input(3)(length - 2, 0))
  io.output(0) := ((w1(length - 2, 0) & (w3(length - 3, 0) ## 0.B)) | (~w1(length - 2, 0) & w2)) ## 0.B
  io.output(1) := w1 ^ (w3 ## 0.B)
}

object Compressor_42 {
  def apply(length: Int = 128)(implicit p: Parameters): Compressor_42 = Module(new Compressor_42(length))
}

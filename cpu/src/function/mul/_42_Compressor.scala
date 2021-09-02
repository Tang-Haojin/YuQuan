package cpu.function.mul

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class _42_Compressor(length: Int = 128)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new _42_CompressorIO(length))
  private val w1 = io.input(0) ^ io.input(1) ^ io.input(2) ^ io.input(3)
  private val w2 = (io.input(0) & io.input(1)) | (io.input(2) & io.input(3))
  private val w3 = (io.input(0) | io.input(1)) & (io.input(2) | io.input(3))
  io.cout := w3(length - 1)
  io.output(0) := ((w1(length - 1) ## w1) & (w3 ## io.cin)) | (~(w1(length - 1) ## w1) & (w2(length - 1) ## w2))
  io.output(1) := (w1(length - 1) ## w1) ^ (w3 ## io.cin)
}

object _42_Compressor {
  def apply(length: Int = 128)(implicit p: Parameters): _42_Compressor = Module(new _42_Compressor(length))
}

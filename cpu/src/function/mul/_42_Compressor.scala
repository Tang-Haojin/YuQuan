package cpu.function.mul

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import cpu.tools._

class _42_Compressor(length: Int = 128)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new _42_CompressorIO(length))
  if (length == 1) {
    val xor = io.input(0) ^ io.input(1) ^ io.input(2) ^ io.input(3)
    io.output(0) := (io.input(0) & ~xor) | (io.cin & xor)
    io.output(1) := xor ^ io.cin
    io.cout      := (io.input(3) & io.input(2)) | (io.input(2) & io.input(1)) | (io.input(3) & io.input(1))
  } else {
    val c0 = _42_Compressor(length - 1)
    val c1 = _42_Compressor(1)
    for (i <- io.input.indices) {
      c0.io.input(i) := io.input(i)(length - 2, 0)
      c1.io.input(i) := io.input(i)(length - 1)
    }
    for (i <- io.output.indices) io.output(i) := c1.io.output(i) ## c0.io.output(i)
    c0.io.cin := io.cin
    c1.io.cin := c0.io.cout
    io.cout   := c1.io.cout
  }
}

object _42_Compressor {
  def apply(length: Int = 128)(implicit p: Parameters): _42_Compressor = Module(new _42_Compressor(length))
}

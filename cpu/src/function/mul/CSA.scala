package cpu.function.mul

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class CSA(length: Int = 128)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new CSAIO(length))
  io.output(0) := io.input(0) ^ io.input(1) ^ io.input(2)
  io.output(1) := ((io.input(0)(length - 2, 0) & io.input(1)(length - 2, 0)) |
                   (io.input(1)(length - 2, 0) & io.input(2)(length - 2, 0)) |
                   (io.input(2)(length - 2, 0) & io.input(0)(length - 2, 0))) ## 0.B
}

object CSA {
  def apply(length: Int = 128)(implicit p: Parameters): CSA = Module(new CSA(length))
}

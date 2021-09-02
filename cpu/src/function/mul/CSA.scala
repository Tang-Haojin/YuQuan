package cpu.function.mul

import chisel3._

class CSA(length: Int = 128) extends RawModule {
  val io = IO(new CSAIO(length))
  io.output(0) := io.input(0) ^ io.input(1) ^ io.input(2)
  io.output(1) := ((io.input(0) & io.input(1)) |
                   (io.input(1) & io.input(2)) |
                   (io.input(2) & io.input(0))) ## 0.B
}

object CSA {
  def apply(length: Int = 128): CSA = Module(new CSA(length))
}

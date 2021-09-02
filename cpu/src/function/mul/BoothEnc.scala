package cpu.function.mul

import chisel3._

class BoothEnc extends RawModule {
  val io = IO(new BoothEncIO)

  io.neg  := (io.code === "b100".U) || (io.code === "b101".U) || (io.code === "b110".U)
  io.zero := (io.code === "b000".U) || (io.code === "b111".U)
  io.one  := (io.code === "b101".U) || (io.code === "b010".U) || (io.code === "b001".U) || (io.code === "b110".U)
  io.two  := (io.code === "b100".U) || (io.code === "b011".U)
}

package sim.cpu

import chisel3._
import cpu._
import cpu.config.GeneralConfig.UseChipLink

class TestTop extends Module {
  val io = IO(new DEBUG)
  val imp =
    if (UseChipLink)
      new TestTop_ChipLink(io, clock, reset)
    else
      new TestTop_Traditional(io, clock, reset)
}

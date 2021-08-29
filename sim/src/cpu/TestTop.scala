package sim.cpu

import chisel3._
import chipsalliance.rocketchip.config._

import utils._
import sim.SimParams

class TestTop(implicit val p: Parameters) extends Module with SimParams {
  val io = IO(new DEBUG)
  val imp =
    if (UseChipLink)
      new TestTop_ChipLink(io, clock, reset)
    else
      new TestTop_Traditional(io, clock, reset)
}

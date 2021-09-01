package utils

import chisel3._
import chisel3.util._
import chisel3.util.random.{LFSRReduce, XOR, PRNG}
import chipsalliance.rocketchip.config._

class MaximalPeriodGaloisLFSR(width: Int, seed: Option[BigInt] = Some(1), reduction: LFSRReduce = XOR)(implicit val p: Parameters) extends chisel3.util.random.MaxPeriodGaloisLFSR(width, seed, reduction) with PrefixParams

object MaximalPeriodGaloisLFSR {
  def apply(
    width: Int,
    increment: Bool = true.B,
    seed: Option[BigInt] = Some(1),
    reduction: LFSRReduce = XOR)(implicit p: Parameters): UInt = PRNG(new MaximalPeriodGaloisLFSR(width, seed, reduction), increment)
}

package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.NumTypes._
import cpu.component.Operators._
import cpu.pipeline.ExecSpecials._
import cpu.pipeline.InstrTypes._
import cpu._

case class Zbb()(implicit val p: Parameters) extends CPUParams {
  private def CPOP   = BitPat("b0110000_00010_?????_001_?????_0010011")
  private def CTZ    = BitPat("b0110000_00001_?????_001_?????_0010011")

  private def CPOPW  = BitPat("b0110000_00010_?????_001_?????_0011011")

  val table = List(
    //           |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    CPOP  -> List(i   , rs1 , non , non , non , cpop, 1.B, norm  ),
    CTZ   -> List(i   , rs1 , non , non , non , ctz , 1.B, norm  )) ++ (if (xlen != 32) List(
    CPOPW -> List(i   , rs1 , non , non , non , cpop, 1.B, word  )) else Nil)
}

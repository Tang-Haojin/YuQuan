package cpu.instruction

import chisel3._
import chisel3.util._

import cpu.pipeline._
import cpu.pipeline.NumTypes._

object Privileged {
  def MRET = BitPat("b0011000_00010_00000_000_00000_1110011")
  def WFI  = BitPat("b0001000_00101_00000_000_00000_1110011")

  val table = Array(
    //            |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special        |
    MRET   -> List(InstrTypes.i, non , non , non , non , non , non , 0.U, ExecSpecials.mret  ),
    WFI    -> List(InstrTypes.i, non , non , non , non , non , non , 0.U, ExecSpecials.non   ) // do nothing
  )
}

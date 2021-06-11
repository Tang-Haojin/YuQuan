package cpu

import chisel3._
import chisel3.util._

import NumTypes._
import Operators._

import cpu.config.GeneralConfig._

object RVM {
  def MUL    = BitPat("b0000001_?????_?????_000_?????_0110011")
  def MULW   = BitPat("b0000001_?????_?????_000_?????_0111011")
  def DIVW   = BitPat("b0000001_?????_?????_100_?????_0111011")

  val table = Array(
    //            |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special        |
    MUL    -> List(InstrTypes.r, rs1 , rs2 , non , non , mul , non , 1.U, ExecSpecials.non   ),
    MULW   -> List(InstrTypes.r, rs1 , rs2 , non , non , mul , non , 1.U, ExecSpecials.word  ),
    DIVW   -> List(InstrTypes.r, rs1 , rs2 , non , non , divw, non , 1.U, ExecSpecials.word  )
  )
}

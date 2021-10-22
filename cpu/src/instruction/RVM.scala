package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.component.Operators._
import cpu.pipeline._
import cpu.pipeline.NumTypes._
import cpu.tools._
import cpu._

case class RVM()(implicit val p: Parameters) extends CPUParams {
  def MUL    = BitPat("b0000001_?????_?????_000_?????_0110011")
  def MULH   = BitPat("b0000001_?????_?????_001_?????_0110011")
  def MULHSU = BitPat("b0000001_?????_?????_010_?????_0110011")
  def MULHU  = BitPat("b0000001_?????_?????_011_?????_0110011")
  def DIV    = BitPat("b0000001_?????_?????_100_?????_0110011")
  def DIVU   = BitPat("b0000001_?????_?????_101_?????_0110011")
  def REM    = BitPat("b0000001_?????_?????_110_?????_0110011")
  def REMU   = BitPat("b0000001_?????_?????_111_?????_0110011")

  def MULW   = BitPat("b0000001_?????_?????_000_?????_0111011")
  def DIVW   = BitPat("b0000001_?????_?????_100_?????_0111011")
  def DIVUW  = BitPat("b0000001_?????_?????_101_?????_0111011")
  def REMW   = BitPat("b0000001_?????_?????_110_?????_0111011")
  def REMUW  = BitPat("b0000001_?????_?????_111_?????_0111011")

  var table = Array(
    //            |    Type    |num1 |num2 |num3 |num4 |op1_2| WB |     Special        |
    MUL    -> List(InstrTypes.r, rs1 , rs2 , non , non , mul , 1.U, ExecSpecials.non   ),
    MULH   -> List(InstrTypes.r, rs1 , rs2 , non , non , mulh, 1.U, ExecSpecials.non   ),
    MULHSU -> List(InstrTypes.r, rs1 , rs2 , non , non , mulh, 1.U, ExecSpecials.msu   ),
    MULHU  -> List(InstrTypes.r, rs1 , rs2 , non , non , mulh, 1.U, ExecSpecials.mu    ),
    DIV    -> List(InstrTypes.r, rs1 , rs2 , non , non , div , 1.U, ExecSpecials.non   ),
    DIVU   -> List(InstrTypes.r, rs1 , rs2 , non , non , divu, 1.U, ExecSpecials.non   ),
    REM    -> List(InstrTypes.r, rs1 , rs2 , non , non , rem , 1.U, ExecSpecials.non   ),
    REMU   -> List(InstrTypes.r, rs1 , rs2 , non , non , remu, 1.U, ExecSpecials.non   )
  )
  if(xlen!=32) table ++= Array(
    MULW   -> List(InstrTypes.r, rs1 , rs2 , non , non , mul , 1.U, ExecSpecials.word  ),
    DIVW   -> List(InstrTypes.r, rs1 , rs2 , non , non , divw, 1.U, ExecSpecials.word  ),
    DIVUW  -> List(InstrTypes.r, rs1 , rs2 , non , non , duw , 1.U, ExecSpecials.word  ),
    REMW   -> List(InstrTypes.r, rs1 , rs2 , non , non , remw, 1.U, ExecSpecials.word  ),
    REMUW  -> List(InstrTypes.r, rs1 , rs2 , non , non , ruw , 1.U, ExecSpecials.word  )
  )
}

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
  def MULW   = 
  if(xlen==64) BitPat("b0000001_?????_?????_000_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def DIVW   =
  if(xlen==64) BitPat("b0000001_?????_?????_100_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def REMW   =
  if(xlen==64) BitPat("b0000001_?????_?????_110_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def REM    = BitPat("b0000001_?????_?????_110_?????_0110011")
  def DIV    = BitPat("b0000001_?????_?????_100_?????_0110011")
  def REMU   = BitPat("b0000001_?????_?????_111_?????_0110011")
  def DIVU   = BitPat("b0000001_?????_?????_101_?????_0110011")
  def MULH   = BitPat("b0000001_?????_?????_001_?????_0110011")
  def DIVUW  = 
  if(xlen==64) BitPat("b0000001_?????_?????_101_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def REMUW  = 
  if(xlen==64) BitPat("b0000001_?????_?????_111_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")

  val table = Array(
    //            |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special        |
    MUL    -> List(InstrTypes.r, rs1 , rs2 , non , non , mul , non , 1.U, ExecSpecials.non   ),
    MULW   -> List(InstrTypes.r, rs1 , rs2 , non , non , mul , non , 1.U, ExecSpecials.word  ),
    DIVW   -> List(InstrTypes.r, rs1 , rs2 , non , non , divw, non , 1.U, ExecSpecials.word  ),
    REMW   -> List(InstrTypes.r, rs1 , rs2 , non , non , remw, non , 1.U, ExecSpecials.word  ),
    REM    -> List(InstrTypes.r, rs1 , rs2 , non , non , rem , non , 1.U, ExecSpecials.non   ),
    DIV    -> List(InstrTypes.r, rs1 , rs2 , non , non , div , non , 1.U, ExecSpecials.non   ),
    REMU   -> List(InstrTypes.r, rs1 , rs2 , non , non , remu, non , 1.U, ExecSpecials.non   ),
    DIVU   -> List(InstrTypes.r, rs1 , rs2 , non , non , divu, non , 1.U, ExecSpecials.non   ),
    MULH   -> List(InstrTypes.r, rs1 , rs2 , non , non , mulh, non , 1.U, ExecSpecials.non   ),
    DIVUW  -> List(InstrTypes.r, rs1 , rs2 , non , non , duw , non , 1.U, ExecSpecials.word  ),
    REMUW  -> List(InstrTypes.r, rs1 , rs2 , non , non , ruw , non , 1.U, ExecSpecials.word  )
  )
}
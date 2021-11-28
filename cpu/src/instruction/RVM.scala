package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.component.Operators._
import cpu.pipeline.NumTypes._
import cpu.pipeline.ExecSpecials._
import cpu.pipeline.InstrTypes._
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

  val table = List(
    //            |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    MUL    -> List(  r , rs1 , rs2 , non , non , mul , 1.B,  norm ),
    MULH   -> List(  r , rs1 , rs2 , non , non , mulh, 1.B,  norm ),
    MULHSU -> List(  r , rs1 , rs2 , non , non , mulh, 1.B,  msu  ),
    MULHU  -> List(  r , rs1 , rs2 , non , non , mulh, 1.B,  mu   ),
    DIV    -> List(  r , rs1 , rs2 , non , non , div , 1.B,  norm ),
    DIVU   -> List(  r , rs1 , rs2 , non , non , divu, 1.B,  norm ),
    REM    -> List(  r , rs1 , rs2 , non , non , rem , 1.B,  norm ),
    REMU   -> List(  r , rs1 , rs2 , non , non , remu, 1.B,  norm )) ++ (if (xlen != 32) List(
    MULW   -> List(  r , rs1 , rs2 , non , non , mul , 1.B,  word ),
    DIVW   -> List(  r , rs1 , rs2 , non , non , divw, 1.B,  word ),
    DIVUW  -> List(  r , rs1 , rs2 , non , non , duw , 1.B,  word ),
    REMW   -> List(  r , rs1 , rs2 , non , non , remw, 1.B,  word ),
    REMUW  -> List(  r , rs1 , rs2 , non , non , ruw , 1.B,  word )) else Nil)
}

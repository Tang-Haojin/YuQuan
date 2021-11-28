package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.ExecSpecials._
import cpu.pipeline.InstrTypes._
import cpu.pipeline.NumTypes._
import cpu.component.Operators._
import cpu.tools._
import cpu._

case class Privileged()(implicit val p: Parameters) extends CPUParams {
  def MRET       = BitPat("b0011000_00010_00000_000_00000_1110011")
  def WFI        = BitPat("b0001000_00101_00000_000_00000_1110011")

  def SRET       = BitPat("b0001000_00010_00000_000_00000_1110011")
  def SFENCE_VMA = BitPat("b0001001_?????_?????_000_00000_1110011")

  val table = List(
    //                |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    MRET       -> List(  i , non , non , non , non , nop , 0.B, mret  ),
    WFI        -> List(  i , non , non , non , non , nop , 0.B, norm  )) /* do nothing */ ++ (if(ext('S')) List(
    SRET       -> List(  i , non , non , non , non , nop , 0.B, sret  ),
    SFENCE_VMA -> List(  i , non , non , non , non , nop , 0.B, sfence)) else Nil)
}

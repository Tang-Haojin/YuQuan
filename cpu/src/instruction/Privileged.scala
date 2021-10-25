package cpu.instruction

import chisel3._
import chisel3.util._

import cpu.pipeline._
import cpu.pipeline.NumTypes._
import chipsalliance.rocketchip.config._
import cpu.tools._
import cpu._

case class Privileged()(implicit val p: Parameters) extends CPUParams {
  def MRET       = BitPat("b0011000_00010_00000_000_00000_1110011")
  def WFI        = BitPat("b0001000_00101_00000_000_00000_1110011")

  def SRET       = BitPat("b0001000_00010_00000_000_00000_1110011")
  def SFENCE_VMA = BitPat("b0001001_?????_?????_000_00000_1110011")

  val table = List(
    //                |    Type    |num1 |num2 |num3 |num4 |op1_2| WB |     Special        |
    MRET       -> List(InstrTypes.i, non , non , non , non , non , 0.U, ExecSpecials.mret  ),
    WFI        -> List(InstrTypes.i, non , non , non , non , non , 0.U, ExecSpecials.non   )) /* do nothing */ ++ (if(ext('S')) List(
    SRET       -> List(InstrTypes.i, non , non , non , non , non , 0.U, ExecSpecials.sret  ),
    SFENCE_VMA -> List(InstrTypes.i, non , non , non , non , non , 0.U, ExecSpecials.sfence)) else Nil)
}

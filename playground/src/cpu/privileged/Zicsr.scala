package cpu.privileged

import chisel3._
import chisel3.util._

import cpu.NumTypes._
import cpu.Operators._
import cpu.{ExecSpecials, InstrTypes}

import cpu.config.GeneralConfig._

object Zicsr {
  def CSRRW  = BitPat("b????????????_?????_001_?????_1110011")
  def CSRRS  = BitPat("b????????????_?????_010_?????_1110011")
  def CSRRC  = BitPat("b????????????_?????_011_?????_1110011")
  def CSRRWI = BitPat("b????????????_?????_101_?????_1110011")
  def CSRRSI = BitPat("b????????????_?????_110_?????_1110011")
  def CSRRCI = BitPat("b????????????_?????_111_?????_1110011")
  def MRET   = BitPat("b001100000010_00000_000_00000_1110011")

  val table = Array(
    //            |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special        |
    CSRRW  -> List(InstrTypes.c, csr , rs1 , non , non , non , 0.U , 1.U, ExecSpecials.csr   ),
    CSRRS  -> List(InstrTypes.c, csr , rs1 , non , non , non , 1.U , 1.U, ExecSpecials.csr   ),
    CSRRC  -> List(InstrTypes.c, csr , rs1 , non , non , non , 2.U , 1.U, ExecSpecials.csr   ),
    CSRRWI -> List(InstrTypes.c, csr , imm , non , non , non , 0.U , 1.U, ExecSpecials.csr   ),
    CSRRSI -> List(InstrTypes.c, csr , imm , non , non , non , 1.U , 1.U, ExecSpecials.csr   ),
    CSRRCI -> List(InstrTypes.c, csr , imm , non , non , non , 2.U , 1.U, ExecSpecials.csr   ),
    MRET   -> List(InstrTypes.i, non , non , non , non , non , non , 0.U, ExecSpecials.mret  )
  )
}

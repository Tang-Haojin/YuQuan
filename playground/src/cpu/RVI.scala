package cpu

import chisel3._
import chisel3.util._

import NumTypes._
import Operators._

import cpu.config.GeneralConfig._

object RVI {
  def ADDI  = BitPat("b???????_?????_?????_000_?????_0010011")
  def AUIPC = BitPat("b???????_?????_?????_???_?????_0010111")
  def JAL   = BitPat("b???????_?????_?????_???_?????_1101111")
  def JALR  = BitPat("b???????_?????_?????_010_?????_1100111")
  def SD    = if (XLEN == 64)
              BitPat("b???????_?????_?????_011_?????_0100011")
              else
              BitPat("b0000000_00000_00000_000_00000_0000000")

  def ERR   = BitPat("b0000000_00000_00000_000_00000_0000000")
  
  val table = Array(
    //           |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special      |
    ADDI  -> List(InstrTypes.i, rs1 , imm , non , non , add , non , 1.U, ExecSpecials.non ),
    AUIPC -> List(InstrTypes.u, pc  , imm , non , non , add , non , 1.U, ExecSpecials.non ),
    JAL   -> List(InstrTypes.j, pc  , four, imm , non , add , add , 1.U, ExecSpecials.jump),
    JALR  -> List(InstrTypes.i, pc  , four, imm , rs1 , add , non , 1.U, ExecSpecials.jalr),
    SD    -> List(InstrTypes.s, rs2 , fun3, rs1 , imm , add , non , 0.U, ExecSpecials.st  )
  )
}

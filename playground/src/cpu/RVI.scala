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
  def JALR  = BitPat("b???????_?????_?????_000_?????_1100111")
  def SD    = if (XLEN == 64)
              BitPat("b???????_?????_?????_011_?????_0100011")
              else
              BitPat("b0000000_00000_00000_000_00000_0000000")
  def LW    = BitPat("b???????_?????_?????_010_?????_0000011")
  def ADDW  = if (XLEN == 64)
              BitPat("b0000000_?????_?????_000_?????_0111011")
              else
              BitPat("b0000000_00000_00000_000_00000_0000000")
  def SUB   = BitPat("b0100000_?????_?????_000_?????_0110011")
  def SLTIU = BitPat("b???????_?????_?????_011_?????_0010011")
  def BEQ   = BitPat("b???????_?????_?????_000_?????_1100011")
  def BNE   = BitPat("b???????_?????_?????_001_?????_1100011")
  def ADDIW = if (XLEN == 64)
              BitPat("b???????_?????_?????_000_?????_0011011")
              else
              BitPat("b0000000_00000_00000_000_00000_0000000")
  def LD    = BitPat("b???????_?????_?????_011_?????_0000011")
  def BLT   = BitPat("b???????_?????_?????_100_?????_1100011")
  def SLT   = BitPat("b0000000_?????_?????_010_?????_0110011")
  def ANDI  = BitPat("b???????_?????_?????_111_?????_0010011")
  def ADD   = BitPat("b0000000_?????_?????_000_?????_0110011")
  def LBU   = BitPat("b???????_?????_?????_100_?????_0000011")
  def BLTU  = BitPat("b???????_?????_?????_110_?????_1100011")

  def TRAP  = BitPat("b???????_?????_?????_???_?????_1101011")
  def ERR   = BitPat("b0000000_00000_00000_000_00000_0000000")
  
  val table = Array(
    //           |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special        |
    ERR   -> List(7.U         , non , non , non , non , non , non , 0.U, ExecSpecials.inv   ),
    ADDI  -> List(InstrTypes.i, rs1 , imm , non , non , add , non , 1.U, ExecSpecials.non   ),
    AUIPC -> List(InstrTypes.u, pc  , imm , non , non , add , non , 1.U, ExecSpecials.non   ),
    JAL   -> List(InstrTypes.j, pc  , four, imm , non , add , add , 1.U, ExecSpecials.jump  ),
    JALR  -> List(InstrTypes.i, pc  , four, imm , rs1 , add , non , 1.U, ExecSpecials.jalr  ),
    SD    -> List(InstrTypes.s, rs2 , non , rs1 , imm , add , fun3, 0.U, ExecSpecials.st    ),
    LW    -> List(InstrTypes.i, non , non , rs1 , imm , non , fun3, 1.U, ExecSpecials.ld    ),
    ADDW  -> List(InstrTypes.r, rs1 , rs2 , non , non , add , non , 1.U, ExecSpecials.word  ),
    SUB   -> List(InstrTypes.r, rs1 , rs2 , non , non , sub , non , 1.U, ExecSpecials.non   ),
    SLTIU -> List(InstrTypes.i, rs1 , imm , non , non , ltu , non , 1.U, ExecSpecials.non   ),
    BEQ   -> List(InstrTypes.b, rs1 , rs2 , imm , non , equ , non , 0.U, ExecSpecials.branch),
    BNE   -> List(InstrTypes.b, rs1 , rs2 , imm , non , neq , non , 0.U, ExecSpecials.branch),
    ADDIW -> List(InstrTypes.i, rs1 , imm , non , non , add , non , 1.U, ExecSpecials.word  ),
    LD    -> List(InstrTypes.i, non , non , rs1 , imm , non , fun3, 1.U, ExecSpecials.ld    ),
    BLT   -> List(InstrTypes.b, rs1 , rs2 , imm , non , lts , non , 0.U, ExecSpecials.branch),
    SLT   -> List(InstrTypes.r, rs1 , rs2 , non , non , lts , non , 1.U, ExecSpecials.non   ),
    ANDI  -> List(InstrTypes.i, rs1 , imm , non , non , and , non , 1.U, ExecSpecials.non   ),
    ADD   -> List(InstrTypes.r, rs1 , rs2 , non , non , add , non , 1.U, ExecSpecials.non   ),
    LBU   -> List(InstrTypes.i, non , non , rs1 , imm , non , fun3, 1.U, ExecSpecials.ld    ),
    BLTU  -> List(InstrTypes.b, rs1 , rs2 , imm , non , ltu , non , 0.U, ExecSpecials.branch),
    TRAP  -> List(InstrTypes.t, imm , non , non , non , add , non , 0.U, ExecSpecials.trap  )
  )
}

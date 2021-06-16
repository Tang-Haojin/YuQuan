package cpu

import chisel3._
import chisel3.util._

import NumTypes._
import Operators._

import cpu.config.GeneralConfig._

object RVI {
  def LUI    = BitPat("b???????_?????_?????_???_?????_0110111")
  def AUIPC  = BitPat("b???????_?????_?????_???_?????_0010111")
  def JAL    = BitPat("b???????_?????_?????_???_?????_1101111")
  def JALR   = BitPat("b???????_?????_?????_000_?????_1100111")
  def BEQ    = BitPat("b???????_?????_?????_000_?????_1100011")
  def BNE    = BitPat("b???????_?????_?????_001_?????_1100011")
  def BLT    = BitPat("b???????_?????_?????_100_?????_1100011")
  def BGE    = BitPat("b???????_?????_?????_101_?????_1100011")
  def BLTU   = BitPat("b???????_?????_?????_110_?????_1100011")
  def BGEU   = BitPat("b???????_?????_?????_111_?????_1100011")
  def LB     = BitPat("b???????_?????_?????_000_?????_0000011")
  def LH     = BitPat("b???????_?????_?????_001_?????_0000011")
  def LW     = BitPat("b???????_?????_?????_010_?????_0000011")
  def LBU    = BitPat("b???????_?????_?????_100_?????_0000011")
  def LHU    = BitPat("b???????_?????_?????_101_?????_0000011")
  def SB     = BitPat("b???????_?????_?????_000_?????_0100011")
  def SH     = BitPat("b???????_?????_?????_001_?????_0100011")
  def SW     = BitPat("b???????_?????_?????_010_?????_0100011")
  def ADDI   = BitPat("b???????_?????_?????_000_?????_0010011")
  def SLTI   = BitPat("b???????_?????_?????_010_?????_0010011")
  def SLTIU  = BitPat("b???????_?????_?????_011_?????_0010011")
  def XORI   = BitPat("b???????_?????_?????_100_?????_0010011")
  def ORI    = BitPat("b???????_?????_?????_110_?????_0010011")
  def ANDI   = BitPat("b???????_?????_?????_111_?????_0010011")
  def SLLI   = 
  if(XLEN==64) BitPat("b000000?_?????_?????_001_?????_0010011")
  else         BitPat("b0000000_?????_?????_001_?????_0010011")
  def SRLI   = 
  if(XLEN==64) BitPat("b000000?_?????_?????_101_?????_0010011")
  else         BitPat("b0000000_?????_?????_101_?????_0010011")
  def SRAI   = 
  if(XLEN==64) BitPat("b010000?_?????_?????_101_?????_0010011")
  else         BitPat("b0100000_?????_?????_101_?????_0010011")
  def ADD    = BitPat("b0000000_?????_?????_000_?????_0110011")
  def SUB    = BitPat("b0100000_?????_?????_000_?????_0110011")
  def SLL    = BitPat("b0000000_?????_?????_001_?????_0110011")
  def SLT    = BitPat("b0000000_?????_?????_010_?????_0110011")
  def SLTU   = BitPat("b0000000_?????_?????_011_?????_0110011")
  def XOR    = BitPat("b0000000_?????_?????_100_?????_0110011")
  def SRL    = BitPat("b0000000_?????_?????_101_?????_0110011")
  def SRA    = BitPat("b0100000_?????_?????_101_?????_0110011")
  def OR     = BitPat("b0000000_?????_?????_110_?????_0110011")
  def AND    = BitPat("b0000000_?????_?????_111_?????_0110011")
  def FENCE  = null
  def ECALL  = null
  def EBREAK = null

  def LWU    =
  if(XLEN==64) BitPat("b???????_?????_?????_110_?????_0000011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def LD     =
  if(XLEN==64) BitPat("b???????_?????_?????_011_?????_0000011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SD     =
  if(XLEN==64) BitPat("b???????_?????_?????_011_?????_0100011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def ADDIW  =
  if(XLEN==64) BitPat("b???????_?????_?????_000_?????_0011011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SLLIW  =
  if(XLEN==64) BitPat("b000000?_?????_?????_001_?????_0011011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SRLIW  =
  if(XLEN==64) BitPat("b000000?_?????_?????_101_?????_0011011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SRAIW  =
  if(XLEN==64) BitPat("b010000?_?????_?????_101_?????_0011011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def ADDW   =
  if(XLEN==64) BitPat("b0000000_?????_?????_000_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SUBW   =
  if(XLEN==64) BitPat("b0100000_?????_?????_000_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SLLW   =
  if(XLEN==64) BitPat("b0000000_?????_?????_001_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SRLW   =
  if(XLEN==64) BitPat("b0000000_?????_?????_101_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
  def SRAW   =
  if(XLEN==64) BitPat("b0100000_?????_?????_101_?????_0111011")
  else         BitPat("b0000000_00000_00000_000_00000_0000000")
 
  def TRAP   = BitPat("b???????_?????_?????_???_?????_1101011")
  def ERR    = BitPat("b0000000_00000_00000_000_00000_0000000")

  val table = Array(
    //           |    Type    |num1 |num2 |num3 |num4 |op1_2|op1_3| WB |     Special        |
    ERR   -> List(7.U         , non , non , non , non , non , non , 0.U, ExecSpecials.inv   ),
    
    LUI   -> List(InstrTypes.u, non , imm , non , non , add , non , 1.U, ExecSpecials.non   ),
    AUIPC -> List(InstrTypes.u, pc  , imm , non , non , add , non , 1.U, ExecSpecials.non   ),
    JAL   -> List(InstrTypes.j, pc  , four, imm , non , add , add , 1.U, ExecSpecials.jump  ),
    JALR  -> List(InstrTypes.i, pc  , four, imm , rs1 , add , non , 1.U, ExecSpecials.jalr  ),
    BEQ   -> List(InstrTypes.b, rs1 , rs2 , imm , non , equ , non , 0.U, ExecSpecials.branch),
    BNE   -> List(InstrTypes.b, rs1 , rs2 , imm , non , neq , non , 0.U, ExecSpecials.branch),
    BLT   -> List(InstrTypes.b, rs1 , rs2 , imm , non , lts , non , 0.U, ExecSpecials.branch),
    BGE   -> List(InstrTypes.b, rs1 , rs2 , imm , non , ges , non , 0.U, ExecSpecials.branch),
    BLTU  -> List(InstrTypes.b, rs1 , rs2 , imm , non , ltu , non , 0.U, ExecSpecials.branch),
    BGEU  -> List(InstrTypes.b, rs1 , rs2 , imm , non , geu , non , 0.U, ExecSpecials.branch),
    LB    -> List(InstrTypes.i, non , non , rs1 , imm , non , 0.U , 1.U, ExecSpecials.ld    ),
    LH    -> List(InstrTypes.i, non , non , rs1 , imm , non , 1.U , 1.U, ExecSpecials.ld    ),
    LW    -> List(InstrTypes.i, non , non , rs1 , imm , non , 2.U , 1.U, ExecSpecials.ld    ),
    LBU   -> List(InstrTypes.i, non , non , rs1 , imm , non , 4.U , 1.U, ExecSpecials.ld    ),
    LHU   -> List(InstrTypes.i, non , non , rs1 , imm , non , 5.U , 1.U, ExecSpecials.ld    ),
    SB    -> List(InstrTypes.s, rs2 , non , rs1 , imm , add , 0.U , 0.U, ExecSpecials.st    ),
    SH    -> List(InstrTypes.s, rs2 , non , rs1 , imm , add , 1.U , 0.U, ExecSpecials.st    ),
    SW    -> List(InstrTypes.s, rs2 , non , rs1 , imm , add , 2.U , 0.U, ExecSpecials.st    ),
    ADDI  -> List(InstrTypes.i, rs1 , imm , non , non , add , non , 1.U, ExecSpecials.non   ),
    SLTI  -> List(InstrTypes.i, rs1 , imm , non , non , lts , non , 1.U, ExecSpecials.non   ),
    SLTIU -> List(InstrTypes.i, rs1 , imm , non , non , ltu , non , 1.U, ExecSpecials.non   ),
    XORI  -> List(InstrTypes.i, rs1 , imm , non , non , xor , non , 1.U, ExecSpecials.non   ),
    ORI   -> List(InstrTypes.i, rs1 , imm , non , non , or  , non , 1.U, ExecSpecials.non   ),
    ANDI  -> List(InstrTypes.i, rs1 , imm , non , non , and , non , 1.U, ExecSpecials.non   ),
    SLLI  -> List(InstrTypes.i, rs1 , imm , non , non , sll , non , 1.U, ExecSpecials.non   ),
    SRLI  -> List(InstrTypes.i, rs1 , imm , non , non , srl , non , 1.U, ExecSpecials.non   ),
    SRAI  -> List(InstrTypes.i, rs1 , imm , non , non , sra , non , 1.U, ExecSpecials.non   ),
    ADD   -> List(InstrTypes.r, rs1 , rs2 , non , non , add , non , 1.U, ExecSpecials.non   ),
    SUB   -> List(InstrTypes.r, rs1 , rs2 , non , non , sub , non , 1.U, ExecSpecials.non   ),
    SLL   -> List(InstrTypes.r, rs1 , rs2 , non , non , sll , non , 1.U, ExecSpecials.non   ),
    SLT   -> List(InstrTypes.r, rs1 , rs2 , non , non , lts , non , 1.U, ExecSpecials.non   ),
    SLTU  -> List(InstrTypes.r, rs1 , rs2 , non , non , ltu , non , 1.U, ExecSpecials.non   ),
    XOR   -> List(InstrTypes.r, rs1 , rs2 , non , non , xor , non , 1.U, ExecSpecials.non   ),
    SRL   -> List(InstrTypes.r, rs1 , rs2 , non , non , srl , non , 1.U, ExecSpecials.non   ),
    SRA   -> List(InstrTypes.r, rs1 , rs2 , non , non , sra , non , 1.U, ExecSpecials.non   ),
    OR    -> List(InstrTypes.r, rs1 , rs2 , non , non , or  , non , 1.U, ExecSpecials.non   ),
    AND   -> List(InstrTypes.r, rs1 , rs2 , non , non , and , non , 1.U, ExecSpecials.non   ),




    LWU   -> List(InstrTypes.i, non , non , rs1 , imm , non , 6.U , 1.U, ExecSpecials.ld    ),
    LD    -> List(InstrTypes.i, non , non , rs1 , imm , non , 3.U , 1.U, ExecSpecials.ld    ),
    SD    -> List(InstrTypes.s, rs2 , non , rs1 , imm , add , 3.U , 0.U, ExecSpecials.st    ),
    ADDIW -> List(InstrTypes.i, rs1 , imm , non , non , add , non , 1.U, ExecSpecials.word  ),
    SLLIW -> List(InstrTypes.i, rs1 , imm , non , non , sllw, non , 1.U, ExecSpecials.word  ),
    SRLIW -> List(InstrTypes.i, rs1 , imm , non , non , srlw, non , 1.U, ExecSpecials.word  ),
    SRAIW -> List(InstrTypes.i, rs1 , imm , non , non , sraw, non , 1.U, ExecSpecials.word  ),
    ADDW  -> List(InstrTypes.r, rs1 , rs2 , non , non , add , non , 1.U, ExecSpecials.word  ),
    SUBW  -> List(InstrTypes.r, rs1 , rs2 , non , non , sub , non , 1.U, ExecSpecials.word  ),
    SLLW  -> List(InstrTypes.r, rs1 , rs2 , non , non , sllw, non , 1.U, ExecSpecials.word  ),
    SRLW  -> List(InstrTypes.r, rs1 , rs2 , non , non , srlw, non , 1.U, ExecSpecials.word  ),
    SRAW  -> List(InstrTypes.r, rs1 , rs2 , non , non , sraw, non , 1.U, ExecSpecials.word  ),

    TRAP  -> List(InstrTypes.i, non , non , non , non , non , non , 0.U, ExecSpecials.trap  )
  )
}

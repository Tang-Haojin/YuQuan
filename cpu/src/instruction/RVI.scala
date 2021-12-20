package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.NumTypes._
import cpu.component.Operators._
import cpu.pipeline.ExecSpecials._
import cpu.pipeline.InstrTypes._
import cpu.tools._
import cpu._

case class RVI()(implicit val p: Parameters) extends CPUParams {
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
  if(xlen!=32) BitPat("b000000?_?????_?????_001_?????_0010011")
  else         BitPat("b0000000_?????_?????_001_?????_0010011")
  def SRLI   = 
  if(xlen!=32) BitPat("b000000?_?????_?????_101_?????_0010011")
  else         BitPat("b0000000_?????_?????_101_?????_0010011")
  def SRAI   = 
  if(xlen!=32) BitPat("b010000?_?????_?????_101_?????_0010011")
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
  def FENCE  = BitPat("b???????_?????_?????_000_?????_0001111")
  def ECALL  = BitPat("b0000000_00000_00000_000_00000_1110011")
  def EBREAK = BitPat("b0000000_00001_00000_000_00000_1110011")

  def LWU    = BitPat("b???????_?????_?????_110_?????_0000011")
  def LD     = BitPat("b???????_?????_?????_011_?????_0000011")
  def SD     = BitPat("b???????_?????_?????_011_?????_0100011")
  def ADDIW  = BitPat("b???????_?????_?????_000_?????_0011011")
  def SLLIW  = BitPat("b000000?_?????_?????_001_?????_0011011")
  def SRLIW  = BitPat("b000000?_?????_?????_101_?????_0011011")
  def SRAIW  = BitPat("b010000?_?????_?????_101_?????_0011011")
  def ADDW   = BitPat("b0000000_?????_?????_000_?????_0111011")
  def SUBW   = BitPat("b0100000_?????_?????_000_?????_0111011")
  def SLLW   = BitPat("b0000000_?????_?????_001_?????_0111011")
  def SRLW   = BitPat("b0000000_?????_?????_101_?????_0111011")
  def SRAW   = BitPat("b0100000_?????_?????_101_?????_0111011")
 
  def TRAP   = BitPat("b???????_?????_?????_???_?????_1101011")

  val table = List(
    //           |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    LUI   -> List(u   , non , imm , non , non , add , 1.B, norm  ),
    AUIPC -> List(u   , pc  , imm , non , non , add , 1.B, norm  ),
    JAL   -> List(j   , pc  , four, imm , non , add , 1.B, norm  ),
    JALR  -> List(i   , pc  , four, imm , rs1 , add , 1.B, norm  ),
    BEQ   -> List(b   , rs1 , rs2 , imm , non , nop , 0.B, norm  ),
    BNE   -> List(b   , rs1 , rs2 , imm , non , nop , 0.B, norm  ),
    BLT   -> List(b   , rs1 , rs2 , imm , non , nop , 0.B, norm  ),
    BGE   -> List(b   , rs1 , rs2 , imm , non , nop , 0.B, norm  ),
    BLTU  -> List(b   , rs1 , rs2 , imm , non , nop , 0.B, norm  ),
    BGEU  -> List(b   , rs1 , rs2 , imm , non , nop , 0.B, norm  ),
    LB    -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    LH    -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    LW    -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    LBU   -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    LHU   -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    SB    -> List(s   , rs2 , non , rs1 , imm , add , 0.B, st    ),
    SH    -> List(s   , rs2 , non , rs1 , imm , add , 0.B, st    ),
    SW    -> List(s   , rs2 , non , rs1 , imm , add , 0.B, st    ),
    ADDI  -> List(i   , rs1 , imm , non , non , add , 1.B, norm  ),
    SLTI  -> List(i   , rs1 , imm , non , non , lts , 1.B, norm  ),
    SLTIU -> List(i   , rs1 , imm , non , non , ltu , 1.B, norm  ),
    XORI  -> List(i   , rs1 , imm , non , non , xor , 1.B, norm  ),
    ORI   -> List(i   , rs1 , imm , non , non , or  , 1.B, norm  ),
    ANDI  -> List(i   , rs1 , imm , non , non , and , 1.B, norm  ),
    SLLI  -> List(i   , rs1 , imm , non , non , sll , 1.B, norm  ),
    SRLI  -> List(i   , rs1 , imm , non , non , srl , 1.B, norm  ),
    SRAI  -> List(i   , rs1 , imm , non , non , sra , 1.B, norm  ),
    ADD   -> List(r   , rs1 , rs2 , non , non , add , 1.B, norm  ),
    SUB   -> List(r   , rs1 , rs2 , non , non , sub , 1.B, norm  ),
    SLL   -> List(r   , rs1 , rs2 , non , non , sll , 1.B, norm  ),
    SLT   -> List(r   , rs1 , rs2 , non , non , lts , 1.B, norm  ),
    SLTU  -> List(r   , rs1 , rs2 , non , non , ltu , 1.B, norm  ),
    XOR   -> List(r   , rs1 , rs2 , non , non , xor , 1.B, norm  ),
    SRL   -> List(r   , rs1 , rs2 , non , non , srl , 1.B, norm  ),
    SRA   -> List(r   , rs1 , rs2 , non , non , sra , 1.B, norm  ),
    OR    -> List(r   , rs1 , rs2 , non , non , or  , 1.B, norm  ),
    AND   -> List(r   , rs1 , rs2 , non , non , and , 1.B, norm  )) ++ (if (!isZmb) List(
    FENCE -> List(i   , non , non , non , non , nop , 0.B, norm  ), // do nothing
    ECALL -> List(i   , non , non , non , non , nop , 0.B, ecall ),
    EBREAK-> List(i   , non , non , non , non , nop , 0.B, ebreak)) else Nil) ++ (if (Debug) List(
    TRAP  -> List(i   , non , non , non , non , nop , 0.B, trap  )) else Nil) ++ (if (xlen != 32) List(
    LWU   -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    LD    -> List(i   , non , non , rs1 , imm , nop , 1.B, ld    ),
    SD    -> List(s   , rs2 , non , rs1 , imm , add , 0.B, st    ),
    ADDIW -> List(i   , rs1 , imm , non , non , add , 1.B, word  ),
    SLLIW -> List(i   , rs1 , imm , non , non , sllw, 1.B, word  ),
    SRLIW -> List(i   , rs1 , imm , non , non , srlw, 1.B, word  ),
    SRAIW -> List(i   , rs1 , imm , non , non , sraw, 1.B, word  ),
    ADDW  -> List(r   , rs1 , rs2 , non , non , add , 1.B, word  ),
    SUBW  -> List(r   , rs1 , rs2 , non , non , sub , 1.B, word  ),
    SLLW  -> List(r   , rs1 , rs2 , non , non , sllw, 1.B, word  ),
    SRLW  -> List(r   , rs1 , rs2 , non , non , srlw, 1.B, word  ),
    SRAW  -> List(r   , rs1 , rs2 , non , non , sraw, 1.B, word  )) else Nil)
}

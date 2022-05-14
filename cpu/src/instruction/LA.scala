package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.LANumTypes._
import cpu.component.Operators._
import cpu.pipeline.ExecSpecials._
import cpu.pipeline.LAInstrTypes._
import cpu._

case class LA()(implicit val p: Parameters) extends CPUParams {
  private def JIRL    = BitPat("b010011_????????????????_?????_?????")
  private def BL      = BitPat("b01010?_????????????????_??????????") // we implement `B` as a pseudo instruction
  private def BEQ     = BitPat("b010110_????????????????_?????_?????")
  private def BNE     = BitPat("b010111_????????????????_?????_?????")
  private def BLT     = BitPat("b011000_????????????????_?????_?????")
  private def BGE     = BitPat("b011001_????????????????_?????_?????")
  private def BLTU    = BitPat("b011010_????????????????_?????_?????")
  private def BGEU    = BitPat("b011011_????????????????_?????_?????")
  private def AND     = BitPat("b00000000000101001_?????_?????_?????")
  private def ANDI    = BitPat("b0000001101_????????????_?????_?????")
  private def ADD     = BitPat("b00000000000100000_?????_?????_?????")
  private def ADDI    = BitPat("b0000001010_????????????_?????_?????")
  private def SUB     = BitPat("b00000000000100010_?????_?????_?????")
  private def SLT     = BitPat("b00000000000100100_?????_?????_?????")
  private def SLTI    = BitPat("b0000001000_????????????_?????_?????")
  private def SLTU    = BitPat("b00000000000100101_?????_?????_?????")
  private def SLTUI   = BitPat("b0000001001_????????????_?????_?????")
  private def LU12I   = BitPat("b0001010_????????????????????_?????")
  private def SLL     = BitPat("b00000000000101110_?????_?????_?????")
  private def SLLI    = BitPat("b00000000010000001_?????_?????_?????")
  private def SRL     = BitPat("b00000000000101111_?????_?????_?????")
  private def SRLI    = BitPat("b00000000010001001_?????_?????_?????")
  private def SRA     = BitPat("b00000000000110000_?????_?????_?????")
  private def SRAI    = BitPat("b00000000010010001_?????_?????_?????")
  private def OR      = BitPat("b00000000000101010_?????_?????_?????")
  private def ORI     = BitPat("b0000001110_????????????_?????_?????")
  private def XOR     = BitPat("b00000000000101011_?????_?????_?????")
  private def XORI    = BitPat("b0000001111_????????????_?????_?????")
  private def NOR     = BitPat("b00000000000101000_?????_?????_?????")
  private def LB      = BitPat("b0010100000_????????????_?????_?????")
  private def LH      = BitPat("b0010100001_????????????_?????_?????")
  private def LW      = BitPat("b0010100010_????????????_?????_?????")
  private def SB      = BitPat("b0010100100_????????????_?????_?????")
  private def SH      = BitPat("b0010100101_????????????_?????_?????")
  private def SW      = BitPat("b0010100110_????????????_?????_?????")
  private def LBU     = BitPat("b0010101000_????????????_?????_?????")
  private def LHU     = BitPat("b0010101001_????????????_?????_?????")
  private def AUIPC   = BitPat("b0001110_????????????????????_?????")
  private def DIV     = BitPat("b00000000001000000_?????_?????_?????")
  private def DIVU    = BitPat("b00000000001000010_?????_?????_?????")
  private def MOD     = BitPat("b00000000001000001_?????_?????_?????")
  private def MODU    = BitPat("b00000000001000011_?????_?????_?????")
  private def MUL     = BitPat("b00000000000111000_?????_?????_?????")
  private def MULH    = BitPat("b00000000000111001_?????_?????_?????")
  private def MULHU   = BitPat("b00000000000111010_?????_?????_?????")
  private def CSROP   = BitPat("b00000100_??????????????_?????_?????")
  private def SYSCALL = BitPat("b00000000001010110_???????????????")
  private def ERTN    = BitPat("b0000011001001000001110_00000_00000")
  //private def FENCE  = BitPat("b???????_?????_?????_000_?????_0001111")
  //private def ECALL  = BitPat("b0000000_00000_00000_000_00000_1110011")
  //private def EBREAK = BitPat("b0000000_00001_00000_000_00000_1110011")
//
  //private def TRAP   = BitPat("b???????_?????_?????_???_?????_1101011")

  val table = List(
    //           |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    JIRL    -> List(i16 , pc  , four, rj  , non , add , 1.B, norm  ),
    BL      -> List(i26 , pc  , four, non , non , add , 1.B, norm  ),
    BEQ     -> List(i16 , rj  , rd  , imm , non , nop , 0.B, norm  ),
    BNE     -> List(i16 , rj  , rd  , imm , non , nop , 0.B, norm  ),
    BLT     -> List(i16 , rj  , rd  , imm , non , nop , 0.B, norm  ),
    BGE     -> List(i16 , rj  , rd  , imm , non , nop , 0.B, norm  ),
    BLTU    -> List(i16 , rj  , rd  , imm , non , nop , 0.B, norm  ),
    BGEU    -> List(i16 , rj  , rd  , imm , non , nop , 0.B, norm  ),
    AND     -> List(r3  , rj  , rk  , non , non , and , 1.B, norm  ),
    ANDI    -> List(i12 , rj  , imm , non , non , and , 1.B, norm  ),
    ADD     -> List(r3  , rj  , rk  , non , non , add , 1.B, norm  ),
    ADDI    -> List(i12 , rj  , imm , non , non , add , 1.B, norm  ),
    SUB     -> List(r3  , rj  , rk  , non , non , sub , 1.B, norm  ),
    SLT     -> List(r3  , rj  , rk  , non , non , lts , 1.B, norm  ),
    SLTI    -> List(i12 , rj  , imm , non , non , lts , 1.B, norm  ),
    SLTU    -> List(r3  , rj  , rk  , non , non , ltu , 1.B, norm  ),
    SLTUI   -> List(i12 , rj  , imm , non , non , ltu , 1.B, norm  ),
    LU12I   -> List(i20 , non , imm , non , non , add , 1.B, norm  ),
    SLL     -> List(r3  , rj  , rk  , non , non , sll , 1.B, norm  ),
    SLLI    -> List(i12 , rj  , imm , non , non , sll , 1.B, norm  ),
    SRL     -> List(r3  , rj  , rk  , non , non , srl , 1.B, norm  ),
    SRLI    -> List(i12 , rj  , imm , non , non , srl , 1.B, norm  ),
    SRA     -> List(r3  , rj  , rk  , non , non , sra , 1.B, norm  ),
    SRAI    -> List(i12 , rj  , imm , non , non , sra , 1.B, norm  ),
    OR      -> List(r3  , rj  , rk  , non , non , or  , 1.B, norm  ),
    ORI     -> List(i12 , rj  , imm , non , non , or  , 1.B, norm  ),
    XOR     -> List(r3  , rj  , rk  , non , non , xor , 1.B, norm  ),
    XORI    -> List(i12 , rj  , imm , non , non , xor , 1.B, norm  ),
    NOR     -> List(r3  , rj  , rk  , non , non , nor , 1.B, norm  ),
    LB      -> List(i12 , non , non , rj  , imm , nop , 1.B, ld    ),
    LH      -> List(i12 , non , non , rj  , imm , nop , 1.B, ld    ),
    LW      -> List(i12 , non , non , rj  , imm , nop , 1.B, ld    ),
    LBU     -> List(i12 , non , non , rj  , imm , nop , 1.B, ld    ),
    LHU     -> List(i12 , non , non , rj  , imm , nop , 1.B, ld    ),
    SB      -> List(i12 , rd  , non , rj  , imm , add , 0.B, st    ),
    SH      -> List(i12 , rd  , non , rj  , imm , add , 0.B, st    ),
    SW      -> List(i12 , rd  , non , rj  , imm , add , 0.B, st    ),
    AUIPC   -> List(i20 , pc  , imm , non , non , add , 1.B, norm  ),
    DIV     -> List(r3  , rj  , rk  , non , non , div , 1.B, norm  ),
    DIVU    -> List(r3  , rj  , rk  , non , non , divu, 1.B, norm  ),
    MOD     -> List(r3  , rj  , rk  , non , non , rem , 1.B, norm  ),
    MODU    -> List(r3  , rj  , rk  , non , non , remu, 1.B, norm  ),
    MUL     -> List(r3  , rj  , rk  , non , non , mul , 1.B, norm  ),
    MULH    -> List(r3  , rj  , rk  , non , non , mulh, 1.B, norm  ),
    MULHU   -> List(r3  , rj  , rk  , non , non , mulh, 1.B, mu    ),
    CSROP   -> List(i12 , csr , rd  , rj  , non , nop , 1.B, zicsr ),
    SYSCALL -> List(i12 , non , non , non , non , nop , 0.B, ecall ),
    ERTN    -> List(i12 , non , non , non , non , nop , 0.B, mret  )
    // ) ++ (if (!isZmb) List(
    // FENCE -> List(i   , non , non , non , non , nop , 0.B, norm  ), // do nothing
    // ECALL -> List(i   , non , non , non , non , nop , 0.B, ecall ),
    // EBREAK-> List(i   , non , non , non , non , nop , 0.B, ebreak)) else Nil) ++ (if (Debug) List(
    // TRAP  -> List(i   , rs1 , non , non , non , nop , 0.B, trap  )) else Nil) 
  )
}

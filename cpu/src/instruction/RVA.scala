package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.ExecSpecials._
import cpu.pipeline.NumTypes._
import cpu.pipeline.InstrTypes._
import cpu.component.Operators._
import cpu._

case class RVA()(implicit val p: Parameters) extends CPUParams {
  private def LR_W      = BitPat("b00010??_00000_?????_010_?????_0101111")
  private def SC_W      = BitPat("b00011??_?????_?????_010_?????_0101111")
  private def AMOSWAP_W = BitPat("b00001??_?????_?????_010_?????_0101111")
  private def AMOADD_W  = BitPat("b00000??_?????_?????_010_?????_0101111")
  private def AMOXOR_W  = BitPat("b00100??_?????_?????_010_?????_0101111")
  private def AMOAND_W  = BitPat("b01100??_?????_?????_010_?????_0101111")
  private def AMOOR_W   = BitPat("b01000??_?????_?????_010_?????_0101111")
  private def AMOMIN_W  = BitPat("b10000??_?????_?????_010_?????_0101111")
  private def AMOMAX_W  = BitPat("b10100??_?????_?????_010_?????_0101111")
  private def AMOMINU_W = BitPat("b11000??_?????_?????_010_?????_0101111")
  private def AMOMAXU_W = BitPat("b11100??_?????_?????_010_?????_0101111")

  private def LR_D      = BitPat("b00010??_00000_?????_011_?????_0101111")
  private def SC_D      = BitPat("b00011??_?????_?????_011_?????_0101111")
  private def AMOSWAP_D = BitPat("b00001??_?????_?????_011_?????_0101111")
  private def AMOADD_D  = BitPat("b00000??_?????_?????_011_?????_0101111")
  private def AMOXOR_D  = BitPat("b00100??_?????_?????_011_?????_0101111")
  private def AMOAND_D  = BitPat("b01100??_?????_?????_011_?????_0101111")
  private def AMOOR_D   = BitPat("b01000??_?????_?????_011_?????_0101111")
  private def AMOMIN_D  = BitPat("b10000??_?????_?????_011_?????_0101111")
  private def AMOMAX_D  = BitPat("b10100??_?????_?????_011_?????_0101111")
  private def AMOMINU_D = BitPat("b11000??_?????_?????_011_?????_0101111")
  private def AMOMAXU_D = BitPat("b11100??_?????_?????_011_?????_0101111")

  val table = List(
    //               |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    LR_W      -> List(  i , non , non , rs1 , non , lr  , 1.B,  amo  ),
    SC_W      -> List(  i , rs2 , non , rs1 , non , sc  , 1.B,  amo  ),
    AMOSWAP_W -> List(  i , rs2 , non , rs1 , non , nop , 1.B,  amo  ),
    AMOADD_W  -> List(  i , rs2 , non , rs1 , non , add , 1.B,  amo  ),
    AMOXOR_W  -> List(  i , rs2 , non , rs1 , non , xor , 1.B,  amo  ),
    AMOAND_W  -> List(  i , rs2 , non , rs1 , non , and , 1.B,  amo  ),
    AMOOR_W   -> List(  i , rs2 , non , rs1 , non , or  , 1.B,  amo  ),
    AMOMIN_W  -> List(  i , rs2 , non , rs1 , non , min , 1.B,  amo  ),
    AMOMAX_W  -> List(  i , rs2 , non , rs1 , non , max , 1.B,  amo  ),
    AMOMINU_W -> List(  i , rs2 , non , rs1 , non , minu, 1.B,  amo  ),
    AMOMAXU_W -> List(  i , rs2 , non , rs1 , non , maxu, 1.B,  amo  )) ++ (if (xlen != 32) List(
    LR_D      -> List(  i , non , non , rs1 , non , lr  , 1.B,  amo  ),
    SC_D      -> List(  i , rs2 , non , rs1 , non , sc  , 1.B,  amo  ),
    AMOSWAP_D -> List(  i , rs2 , non , rs1 , non , nop , 1.B,  amo  ),
    AMOADD_D  -> List(  i , rs2 , non , rs1 , non , add , 1.B,  amo  ),
    AMOXOR_D  -> List(  i , rs2 , non , rs1 , non , xor , 1.B,  amo  ),
    AMOAND_D  -> List(  i , rs2 , non , rs1 , non , and , 1.B,  amo  ),
    AMOOR_D   -> List(  i , rs2 , non , rs1 , non , or  , 1.B,  amo  ),
    AMOMIN_D  -> List(  i , rs2 , non , rs1 , non , min , 1.B,  amo  ),
    AMOMAX_D  -> List(  i , rs2 , non , rs1 , non , max , 1.B,  amo  ),
    AMOMINU_D -> List(  i , rs2 , non , rs1 , non , minu, 1.B,  amo  ),
    AMOMAXU_D -> List(  i , rs2 , non , rs1 , non , maxu, 1.B,  amo  )) else Nil)
}

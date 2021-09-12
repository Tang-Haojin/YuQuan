package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.ExecSpecials.{ non => _, _ }
import cpu.pipeline.NumTypes._
import cpu.component.Operators._
import cpu.pipeline._
import cpu.tools._
import cpu._

case class RVA()(implicit val p: Parameters) extends CPUParams {
  def LR_W      = BitPat("b00010??_00000_?????_010_?????_0101111")
  def SC_W      = BitPat("b00011??_?????_?????_010_?????_0101111")
  def AMOSWAP_W = BitPat("b00001??_?????_?????_010_?????_0101111")
  def AMOADD_W  = BitPat("b00000??_?????_?????_010_?????_0101111")
  def AMOXOR_W  = BitPat("b00100??_?????_?????_010_?????_0101111")
  def AMOAND_W  = BitPat("b01100??_?????_?????_010_?????_0101111")
  def AMOOR_W   = BitPat("b01000??_?????_?????_010_?????_0101111")
  def AMOMIN_W  = BitPat("b10000??_?????_?????_010_?????_0101111")
  def AMOMAX_W  = BitPat("b10100??_?????_?????_010_?????_0101111")
  def AMOMINU_W = BitPat("b11000??_?????_?????_010_?????_0101111")
  def AMOMAXU_W = BitPat("b11100??_?????_?????_010_?????_0101111")

  def LR_D      = if(xlen!=32) BitPat("b00010??_00000_?????_011_?????_0101111") else RVI().ERR
  def SC_D      = if(xlen!=32) BitPat("b00011??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOSWAP_D = if(xlen!=32) BitPat("b00001??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOADD_D  = if(xlen!=32) BitPat("b00000??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOXOR_D  = if(xlen!=32) BitPat("b00100??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOAND_D  = if(xlen!=32) BitPat("b01100??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOOR_D   = if(xlen!=32) BitPat("b01000??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOMIN_D  = if(xlen!=32) BitPat("b10000??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOMAX_D  = if(xlen!=32) BitPat("b10100??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOMINU_D = if(xlen!=32) BitPat("b11000??_?????_?????_011_?????_0101111") else RVI().ERR
  def AMOMAXU_D = if(xlen!=32) BitPat("b11100??_?????_?????_011_?????_0101111") else RVI().ERR

  val table = Array(
    //               |Type|num1 |num2 |num3 |num4 |op1_2|op1_3| WB |Special|
    LR_W      -> List(0.U , non , non , rs1 , non , lr  , 2.U , 1.U,  amo  ),
    SC_W      -> List(0.U , rs2 , non , rs1 , non , sc  , 2.U , 1.U,  amo  ),
    AMOSWAP_W -> List(0.U , rs2 , tmp , rs1 , non , non , 2.U , 1.U,  amo  ),
    AMOADD_W  -> List(0.U , rs2 , tmp , rs1 , non , add , 2.U , 1.U,  amo  ),
    AMOXOR_W  -> List(0.U , rs2 , tmp , rs1 , non , xor , 2.U , 1.U,  amo  ),
    AMOAND_W  -> List(0.U , rs2 , tmp , rs1 , non , and , 2.U , 1.U,  amo  ),
    AMOOR_W   -> List(0.U , rs2 , tmp , rs1 , non , or  , 2.U , 1.U,  amo  ),
    AMOMIN_W  -> List(0.U , rs2 , tmp , rs1 , non , min , 2.U , 1.U,  amo  ),
    AMOMAX_W  -> List(0.U , rs2 , tmp , rs1 , non , max , 2.U , 1.U,  amo  ),
    AMOMINU_W -> List(0.U , rs2 , tmp , rs1 , non , minu, 2.U , 1.U,  amo  ),
    AMOMAXU_W -> List(0.U , rs2 , tmp , rs1 , non , maxu, 2.U , 1.U,  amo  ),
    
    LR_D      -> List(0.U , non , non , rs1 , non , lr  , 3.U , 1.U,  amo  ),
    SC_D      -> List(0.U , rs2 , non , rs1 , non , sc  , 3.U , 1.U,  amo  ),
    AMOSWAP_D -> List(0.U , rs2 , tmp , rs1 , non , non , 3.U , 1.U,  amo  ),
    AMOADD_D  -> List(0.U , rs2 , tmp , rs1 , non , add , 3.U , 1.U,  amo  ),
    AMOXOR_D  -> List(0.U , rs2 , tmp , rs1 , non , xor , 3.U , 1.U,  amo  ),
    AMOAND_D  -> List(0.U , rs2 , tmp , rs1 , non , and , 3.U , 1.U,  amo  ),
    AMOOR_D   -> List(0.U , rs2 , tmp , rs1 , non , or  , 3.U , 1.U,  amo  ),
    AMOMIN_D  -> List(0.U , rs2 , tmp , rs1 , non , min , 3.U , 1.U,  amo  ),
    AMOMAX_D  -> List(0.U , rs2 , tmp , rs1 , non , max , 3.U , 1.U,  amo  ),
    AMOMINU_D -> List(0.U , rs2 , tmp , rs1 , non , minu, 3.U , 1.U,  amo  ),
    AMOMAXU_D -> List(0.U , rs2 , tmp , rs1 , non , maxu, 3.U , 1.U,  amo  )
  )
}

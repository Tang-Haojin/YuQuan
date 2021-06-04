package cpu

import chisel3._
import chisel3.util._

import InstrTypes._

object RVI {
  def ADDI = BitPat("b???????????_?????_000_?????_0010011")
  
  val table = Array(
    //          |    Type    |    num1     |    num2     |    num3     |    ALUType   | WB |
    ADDI -> List(InstrTypes.i, NumTypes.rs1, NumTypes.imm, NumTypes.non, Operators.add, 1.U)
  )
}
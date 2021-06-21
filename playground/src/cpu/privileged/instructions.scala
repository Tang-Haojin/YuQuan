package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._

object Privileged {
  def MRET = BitPat("b0011000_00010_00000_000_00000_1110011")
  def WFI  = BitPat("b0001000_00101_00000_000_00000_1110011")
}
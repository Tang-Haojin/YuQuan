package cpu.instruction

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline.NumTypes._
import cpu.component.Operators._
import cpu.pipeline.ExecSpecials._
import cpu.pipeline.InstrTypes._
import cpu._

case class Zifencei()(implicit val p: Parameters) extends CPUParams {
  private def FENCE_I = BitPat("b???????_?????_?????_001_?????_0001111")

  val table = Array(
    //             |Type|num1 |num2 |num3 |num4 |op1_2| WB |Special|
    FENCE_I -> List(  i , non , non , non , non , nop , 0.B, fencei)
  )
}

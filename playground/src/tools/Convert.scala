package tools

import chisel3._

object Convert {
  import scala.language.implicitConversions
  implicit def Bits2UInt(x: Bits): UInt = x.asUInt()
}

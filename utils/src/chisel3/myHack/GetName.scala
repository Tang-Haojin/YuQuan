package chisel3.myHack

import chisel3._
import chisel3.internal.Builder._

object GetName {
  def apply[T <: Data](that: T): String = that._computeName(None, None).get
}

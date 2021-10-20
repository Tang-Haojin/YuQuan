package chisel3.myHack

import chisel3._
import chisel3.internal.Builder._

object GetName {
  // TODO: in chisel 3.5.x, we should use `_computeName`
  def apply[T <: Data](that: T): String = that.computeName(None, None).get
}

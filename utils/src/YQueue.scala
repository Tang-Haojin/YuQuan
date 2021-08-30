package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

class YQueue[T <: Data](gen: T, entries: Int, pipe: Boolean = false, flow: Boolean = false)
                       (implicit compileOptions: chisel3.CompileOptions, val p: Parameters)
    extends Queue[T](gen, entries, pipe, flow) with PrefixParams {
  override val desiredName = modulePrefix + this.getClass().getSimpleName()
}

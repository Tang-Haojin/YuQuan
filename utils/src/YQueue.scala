package utils

import chisel3._
import chipsalliance.rocketchip.config._

class Queue[T <: Data](gen: T, entries: Int, pipe: Boolean = false, flow: Boolean = false) (implicit val p: Parameters)
    extends chisel3.util.Queue[T](gen, entries, pipe, flow) with PrefixParams

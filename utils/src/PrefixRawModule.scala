package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

abstract class PrefixRawModule(implicit val p: Parameters) extends RawModule with PrefixParams {
  implicit class Connect[T <: Bundle](x: T) {
    def connect(elems: (T => Unit)*): Unit = elems.foreach(_(x))
  }
}

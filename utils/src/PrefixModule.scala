package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

abstract class PrefixModule(implicit val p: Parameters) extends Module with PrefixParams {
  override val desiredName = modulePrefix + this.getClass().getSimpleName()
}

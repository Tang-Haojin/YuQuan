package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

abstract trait UtilsParams {
  implicit val p: Parameters
  val xlen   = p(XLEN)
  val axSize = p(AxSIZE)
  val alen   = p(ALEN)
  val idlen  = p(IDLEN)
}

case object XLEN   extends Field[Int]
case object AxSIZE extends Field[Int]
case object ALEN   extends Field[Int]
case object IDLEN  extends Field[Int]

abstract trait PrefixParams {
  implicit val p: Parameters
  val modulePrefix = p(MODULE_PREFIX)
}

case object MODULE_PREFIX extends Field[String]

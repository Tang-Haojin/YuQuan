package utils

import chisel3._
import chisel3.util._
import chisel3.experimental.BaseModule
import chipsalliance.rocketchip.config._

abstract trait UtilsParams {
  implicit val p: Parameters
  val xlen      = p(XLEN)
  val axSize    = log2Ceil(xlen / 8)
  val alen      = p(ALEN)
  val idlen     = p(IDLEN)
  val usrlen    = p(USRLEN)
  val useqos    = p(USEQOS)
  val useprot   = p(USEPROT)
  val usecache  = p(USECACHE)
  val uselock   = p(USELOCK)
  val useregion = p(USEREGION)
  val axirename = p(AXIRENAME)
  val useXilinx = p(USEXILINX)
  val isAxi3    = p(ISAXI3)
  implicit class UtilsParamsConnect[T <: Bundle](x: T) {
    def seqmap(elems: Seq[T => Unit]): T = { elems.foreach(_(x)); x }
    def connect(elems: (T => Unit)*): T = seqmap(elems)
    def replace(elems: (T => Unit)*): T = WireDefault(x).seqmap(elems)
  }
}

case object XLEN      extends Field[Int]
case object ALEN      extends Field[Int]
case object IDLEN     extends Field[Int]
case object USRLEN    extends Field[Int]
case object USEQOS    extends Field[Int]
case object USEPROT   extends Field[Int]
case object USECACHE  extends Field[Int]
case object USELOCK   extends Field[Int]
case object USEREGION extends Field[Int]
case object AXIRENAME extends Field[Boolean]
case object USEXILINX extends Field[Boolean]
case object ISAXI3    extends Field[Boolean]

abstract trait PrefixParams extends BaseModule {
  implicit val p: Parameters
  val YQModulePrefix = p(MODULE_PREFIX)
  override val desiredName = YQModulePrefix + this.getClass().getSimpleName()
}

case object MODULE_PREFIX extends Field[String]

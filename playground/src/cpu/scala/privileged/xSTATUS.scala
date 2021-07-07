package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._

class MSTATUS(mstatus: UInt, init: Option[UInt]){
  val reg = if (init.isEmpty) mstatus else RegInit(mstatus, init.get)

  val UIE = WireDefault(reg(0))
  val SIE = WireDefault(reg(1))
  val WPRI_2 = WireDefault(reg(2))
  val MIE = WireDefault(reg(3))
  val UPIE = WireDefault(reg(4))
  val SPIE = WireDefault(reg(5))
  val WPRI_6 = WireDefault(reg(6))
  val MPIE = WireDefault(reg(7))
  val SPP = WireDefault(reg(8))
  val WPRI_9 = WireDefault(reg(10, 9))
  val MPP = WireDefault(reg(12, 11))
  val FS = WireDefault(reg(14, 13))
  val XS = WireDefault(reg(16, 15))
  val MPRV = WireDefault(reg(17))
  val SUM = WireDefault(reg(18))
  val MXR = WireDefault(reg(19))
  val TVM = WireDefault(reg(20))
  val TW = WireDefault(reg(21))
  val TSR = WireDefault(reg(22))
  val WPRI_23 = WireDefault(reg(if (XLEN != 32) 31 else 30, 23))
  val UXL = if (XLEN != 32) WireDefault(reg(33, 32)) else null
  val SXL = if (XLEN != 32) WireDefault(reg(35, 34)) else null
  val WPRI_36 = if (XLEN != 32) WireDefault(reg(XLEN - 2, 36)) else null
  val SD = WireDefault(reg(XLEN - 1))
}

class MIP(mip: UInt, init: Option[UInt]){
  val reg = if (init.isEmpty) mip else RegInit(mip, init.get)

  val USIP    = WireDefault(reg(0))
  val SSIP    = WireDefault(reg(1))
  val WPRI_2  = WireDefault(reg(2))
  val MSIP    = WireDefault(reg(3))
  val UTIP    = WireDefault(reg(4))
  val STIP    = WireDefault(reg(5))
  val WPRI_6  = WireDefault(reg(6))
  val MTIP    = WireDefault(reg(7))
  val UEIP    = WireDefault(reg(8))
  val SEIP    = WireDefault(reg(9))
  val WPRI_10 = WireDefault(reg(10))
  val MEIP    = WireDefault(reg(11))
  val WPRI_12 = WireDefault(reg(XLEN - 1, 12))
}

class MIE(mie: UInt, init: Option[UInt]){
  val reg = if (init.isEmpty) mie else RegInit(mie, init.get)

  val USIE    = WireDefault(reg(0))
  val SSIE    = WireDefault(reg(1))
  val WPRI_2  = WireDefault(reg(2))
  val MSIE    = WireDefault(reg(3))
  val UTIE    = WireDefault(reg(4))
  val STIE    = WireDefault(reg(5))
  val WPRI_6  = WireDefault(reg(6))
  val MTIE    = WireDefault(reg(7))
  val UEIE    = WireDefault(reg(8))
  val SEIE    = WireDefault(reg(9))
  val WPRI_10 = WireDefault(reg(10))
  val MEIE    = WireDefault(reg(11))
  val WPRI_12 = WireDefault(reg(XLEN - 1, 12))
}

object MSTATUS {
  import scala.language.implicitConversions
  implicit def getval(x: MSTATUS): UInt = x.reg
}

object MIP {
  import scala.language.implicitConversions
  implicit def getval(x: MIP): UInt = x.reg
}

object MIE {
  import scala.language.implicitConversions
  implicit def getval(x: MIE): UInt = x.reg
}

object MstatusInit {
  /** Construct a [[MSTATUS]] wrapper with [[UInt]].
    * @param mstatus A [[UInt]] to be decoded as mstatus
    */
  def apply(mstatus: UInt): MSTATUS = new MSTATUS(mstatus, None)

  /** Construct a [[MSTATUS]] from a type template initialized to the specified value on reset
    * @param t The type template used to construct the Mstatus
    * @param init The value the Mstatus is initialized to on reset
    */
  def apply(t: UInt, init: UInt): MSTATUS = new MSTATUS(t, Some(init))
}

object MipInit {
  /** Construct a [[MIP]] wrapper with [[UInt]].
    * @param mip A [[UInt]] to be decoded as mip
    */
  def apply(mip: UInt): MIP = new MIP(mip, None)

  /** Construct a [[MIP]] from a type template initialized to the specified value on reset
    * @param t The type template used to construct the Mip
    * @param init The value the Mip is initialized to on reset
    */
  def apply(t: UInt, init: UInt): MIP = new MIP(t, Some(init))
}

object MieInit {
  /** Construct a [[MIE]] wrapper with [[UInt]].
    * @param mie A [[UInt]] to be decoded as mie
    */
  def apply(mie: UInt): MIE = new MIE(mie, None)

  /** Construct a [[MIE]] from a type template initialized to the specified value on reset
    * @param t The type template used to construct the Mie
    * @param init The value the Mie is initialized to on reset
    */
  def apply(t: UInt, init: UInt): MIE = new MIE(t, Some(init))
}

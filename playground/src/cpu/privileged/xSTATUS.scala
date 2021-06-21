package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._

class MSTATUS(mstatus: UInt) {
  val reg = mstatus

  val UIE = reg(0)
  val SIE = reg(1)
  val WPRI_2 = reg(2)
  val MIE = reg(3)
  val UPIE = reg(4)
  val SPIE = reg(5)
  val WPRI_6 = reg(6)
  val MPIE = reg(7)
  val SPP = reg(8)
  val WPRI_9 = reg(10, 9)
  val MPP = reg(12, 11)
  val FS = reg(14, 13)
  val XS = reg(16, 15)
  val MPRV = reg(17)
  val SUM = reg(18)
  val MXR = reg(19)
  val TVM = reg(20)
  val TW = reg(21)
  val TSR = reg(22)
  val WPRI_23 = reg(if (XLEN != 32) 31 else 30, 23)
  val UXL = if (XLEN != 32) reg(33, 32) else null
  val SXL = if (XLEN != 32) reg(35, 34) else null
  val WPRI_36 = if (XLEN != 32) reg(XLEN - 2, 36) else null
  val SD = reg(XLEN - 1)
}

class MIP(mip: UInt) {
  val reg = mip

  val USIP    = reg(0)
  val SSIP    = reg(1)
  val WPRI_2  = reg(2)
  val MSIP    = reg(3)
  val UTIP    = reg(4)
  val STIP    = reg(5)
  val WPRI_6  = reg(6)
  val MTIP    = reg(7)
  val UEIP    = reg(8)
  val SEIP    = reg(9)
  val WPRI_10 = reg(10)
  val MEIP    = reg(11)
  val WPRI_12 = reg(XLEN - 1, 12)
}

class MIE(mie: UInt) {
  val reg = mie

  val USIE    = reg(0)
  val SSIE    = reg(1)
  val WPRI_2  = reg(2)
  val MSIE    = reg(3)
  val UTIE    = reg(4)
  val STIE    = reg(5)
  val WPRI_6  = reg(6)
  val MTIE    = reg(7)
  val UEIE    = reg(8)
  val SEIE    = reg(9)
  val WPRI_10 = reg(10)
  val MEIE    = reg(11)
  val WPRI_12 = reg(XLEN - 1, 12)
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
  def apply(mstatus: UInt): MSTATUS = new MSTATUS(mstatus)
}

object MipInit {
  /** Construct a [[MIP]] wrapper with [[UInt]].
    * @param mip A [[UInt]] to be decoded as mip
    */
  def apply(mip: UInt): MIP = new MIP(mip)
}

object MieInit {
  /** Construct a [[MIE]] wrapper with [[UInt]].
    * @param mie A [[UInt]] to be decoded as mie
    */
  def apply(mie: UInt): MIE = new MIE(mie)
}

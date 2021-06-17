package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._

class MSTATUS(init: UInt) {
  val mstatus = RegInit(init)

  val UIE = mstatus(0)
  val SIE = mstatus(1)
  val MIE = mstatus(3)
  val UPIE = mstatus(4)
  val SPIE = mstatus(5)
  val MPIE = mstatus(7)
  val SPP = mstatus(8)
  val MPP = mstatus(12, 11)
  val FS = mstatus(14, 13)
  val XS = mstatus(16, 15)
  val MPRV = mstatus(17)
  val SUM = mstatus(18)
  val MXR = mstatus(19)
  val TVM = mstatus(20)
  val TW = mstatus(21)
  val TSR = mstatus(22)
  val UXL = if (XLEN == 64) mstatus(33, 32) else null
  val SXL = if (XLEN == 64) mstatus(35, 34) else null
  val SD = mstatus(XLEN - 1)
}

object MSTATUS {
  import scala.language.implicitConversions
  implicit def getval(x: MSTATUS) : UInt = x.mstatus
}

object MstatusInit {
  /** Construct a [[MSTATUS]] initialized on reset to the specified value.
    * @param init Initial value that serves as a type template and reset value
    */
  def apply(init: UInt): MSTATUS = new MSTATUS(init)
}

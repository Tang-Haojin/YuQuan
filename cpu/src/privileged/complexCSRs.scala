package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._
import chipsalliance.rocketchip.config._
import cpu.tools._
import cpu._

class MSTATUS(mstatus: UInt, init: Option[UInt])(implicit val p: Parameters) extends CPUParams {
  val reg = if (init.isEmpty) mstatus else RegInit(mstatus, init.get)

  val UIE  = WireDefault(reg(0))
  val SIE  = WireDefault(reg(1))
  val MIE  = WireDefault(reg(3))
  val UPIE = WireDefault(reg(4))
  val SPIE = WireDefault(reg(5))
  val MPIE = WireDefault(reg(7))
  val SPP  = WireDefault(reg(8))
  val MPP  = WireDefault(reg(12, 11))
  val FS   = WireDefault(reg(14, 13))
  val XS   = WireDefault(reg(16, 15))
  val MPRV = WireDefault(reg(17))
  val SUM  = WireDefault(reg(18))
  val MXR  = WireDefault(reg(19))
  val TVM  = WireDefault(reg(20))
  val TW   = WireDefault(reg(21))
  val TSR  = WireDefault(reg(22))
  val UXL  = if (xlen != 32) WireDefault(reg(33, 32)) else null
  val SXL  = if (xlen != 32) WireDefault(reg(35, 34)) else null
  val SD   = WireDefault(reg(xlen - 1))

  val upperList = List(0.U((xlen - 37).W), SXL, UXL)
  val lowerList = List(0.U((if (xlen != 32) 9 else 8).W), 
                       TSR, TW, TVM, MXR, SUM, MPRV, XS, FS, MPP, 0.B, SPP,
                       MPIE, 0.B, SPIE, UPIE, MIE, 0.B, SIE, UIE)

  val upper = Cat(upperList)
  val lower = Cat(lowerList)

  if (init.isDefined)
    reg := (if (xlen != 32) Cat(SD, upper, lower) else Cat(SD, lower))

  final def := (that: => MSTATUS): Unit = {
    this.SD := that.SD
    if (xlen != 32)
      for (i <- this.upperList.indices)
        if (!this.upperList(i).isLit) this.upperList(i) := that.upperList(i)
    for (i <- this.lowerList.indices)
      if (!this.lowerList(i).isLit) this.lowerList(i) := that.lowerList(i)
  }
}

class MIP(mip: UInt, init: Option[UInt])(implicit val p: Parameters) extends CPUParams {
  val reg = if (init.isEmpty) mip else RegInit(mip, init.get)

  val USIP = WireDefault(reg(0))
  val SSIP = WireDefault(reg(1))
  val MSIP = WireDefault(reg(3))
  val UTIP = WireDefault(reg(4))
  val STIP = WireDefault(reg(5))
  val MTIP = WireDefault(reg(7))
  val UEIP = WireDefault(reg(8))
  val SEIP = WireDefault(reg(9))
  val MEIP = WireDefault(reg(11))

  if (init.isDefined)
    reg := Cat(0.U((xlen - 12).W),
               MEIP, 0.B, SEIP, UEIP,
               MTIP, 0.B, STIP, UTIP,
               MSIP, 0.B, SSIP, USIP)

  final def := (that: => MIP): Unit = {
    this.MSIP := that.MSIP
    this.MTIP := that.MTIP
    this.MEIP := that.MEIP
    if (extensions.contains('S')) {
      this.SSIP := that.SSIP
      this.STIP := that.STIP
      this.SEIP := that.SEIP
    }
    if (extensions.contains('U')) {
      this.USIP := that.USIP
      this.UTIP := that.UTIP
      this.UEIP := that.UEIP
    }
  }
}

class MIE(mie: UInt, init: Option[UInt])(implicit val p: Parameters) extends CPUParams {
  val reg = if (init.isEmpty) mie else RegInit(mie, init.get)

  val USIE = WireDefault(reg(0))
  val SSIE = WireDefault(reg(1))
  val MSIE = WireDefault(reg(3))
  val UTIE = WireDefault(reg(4))
  val STIE = WireDefault(reg(5))
  val MTIE = WireDefault(reg(7))
  val UEIE = WireDefault(reg(8))
  val SEIE = WireDefault(reg(9))
  val MEIE = WireDefault(reg(11))

  if (init.isDefined)
    reg := Cat(0.U((xlen - 12).W),
               MEIE, 0.B, SEIE, UEIE,
               MTIE, 0.B, STIE, UTIE,
               MSIE, 0.B, SSIE, USIE)

  final def := (that: => MIE): Unit = {
    this.MSIE := that.MSIE
    this.MTIE := that.MTIE
    this.MEIE := that.MEIE
    if (extensions.contains('S')) {
      this.SSIE := that.SSIE
      this.STIE := that.STIE
      this.SEIE := that.SEIE
    }
    if (extensions.contains('U')) {
      this.USIE := that.USIE
      this.UTIE := that.UTIE
      this.UEIE := that.UEIE
    }
  }
}

object MSTATUS {
  import scala.language.implicitConversions
  implicit def getval(x: MSTATUS): UInt = x.reg
  implicit def wrapit(x: UInt)(implicit p: Parameters): MSTATUS = MstatusInit(x)
}

object MIP {
  import scala.language.implicitConversions
  implicit def getval(x: MIP): UInt = x.reg
  implicit def wrapit(x: UInt)(implicit p: Parameters): MIP = MipInit(x)
}

object MIE {
  import scala.language.implicitConversions
  implicit def getval(x: MIE): UInt = x.reg
  implicit def wrapit(x: UInt)(implicit p: Parameters): MIE = MieInit(x)
}

object MstatusInit {
  /** Construct a [[MSTATUS]] wrapper with [[UInt]].
    * @param mstatus A [[UInt]] to be decoded as mstatus
    */
  def apply(mstatus: UInt)(implicit p: Parameters): MSTATUS = new MSTATUS(mstatus, None)

  /** Construct a [[MSTATUS]] from a type template initialized to the specified value on reset
    * @param t The type template used to construct the Mstatus
    * @param init The value the Mstatus is initialized to on reset
    */
  def apply(t: UInt, init: UInt)(implicit p: Parameters): MSTATUS = new MSTATUS(t, Some(init))
}

object MipInit {
  /** Construct a [[MIP]] wrapper with [[UInt]].
    * @param mip A [[UInt]] to be decoded as mip
    */
  def apply(mip: UInt)(implicit p: Parameters): MIP = new MIP(mip, None)

  /** Construct a [[MIP]] from a type template initialized to the specified value on reset
    * @param t The type template used to construct the Mip
    * @param init The value the Mip is initialized to on reset
    */
  def apply(t: UInt, init: UInt)(implicit p: Parameters): MIP = new MIP(t, Some(init))
}

object MieInit {
  /** Construct a [[MIE]] wrapper with [[UInt]].
    * @param mie A [[UInt]] to be decoded as mie
    */
  def apply(mie: UInt)(implicit p: Parameters): MIE = new MIE(mie, None)

  /** Construct a [[MIE]] from a type template initialized to the specified value on reset
    * @param t The type template used to construct the Mie
    * @param init The value the Mie is initialized to on reset
    */
  def apply(t: UInt, init: UInt)(implicit p: Parameters): MIE = new MIE(t, Some(init))
}

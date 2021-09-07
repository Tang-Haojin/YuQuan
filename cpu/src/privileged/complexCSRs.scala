package cpu.privileged

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu._

class MstatusBundle(implicit val p: Parameters) extends Bundle with CPUParams {
  val SD     = UInt(1.W)
  val WPRI_0 = UInt((if (xlen == 32) 8 else xlen - 37).W)
  val SXL    = UInt((if (xlen == 32) 0 else 2).W)
  val UXL    = UInt((if (xlen == 32) 0 else 2).W)
  val WPRI_1 = UInt((if (xlen == 32) 0 else 9).W)
  val TSR    = UInt(1.W)
  val TW     = UInt(1.W)
  val TVM    = UInt(1.W)
  val MXR    = UInt(1.W)
  val SUM    = UInt(1.W)
  val MPRV   = UInt(1.W)
  val XS     = UInt(2.W)
  val FS     = UInt(2.W)
  val MPP    = UInt(2.W)
  val WPRI_2 = UInt(2.W)
  val SPP    = UInt(1.W)
  val MPIE   = Bool()
  val WPRI_3 = Bool()
  val SPIE   = Bool()
  val UPIE   = Bool()
  val MIE    = Bool()
  val WPRI_4 = Bool()
  val SIE    = Bool()
  val UIE    = Bool()
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

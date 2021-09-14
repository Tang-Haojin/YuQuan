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
  val TSR    = Bool()
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

  def := (that: => Data): Unit = {
    val mstatus = WireDefault(new MstatusBundle, that.asTypeOf(new MstatusBundle))
    MXR := mstatus.MXR; SUM := mstatus.SUM
    MPP := Mux(mstatus.MPP =/= 2.U, mstatus.MPP, MPP)
    SPP := mstatus.SPP; MPIE := mstatus.MPIE; SPIE := mstatus.SPIE; UPIE := mstatus.UPIE
    MIE := mstatus.MIE; SIE := mstatus.SIE; UIE := mstatus.UIE; TSR := mstatus.TSR
  }
}

class MipBundle(implicit val p: Parameters) extends Bundle with CPUParams {
  val WPRI_0 = UInt((xlen - 12).W)
  val MEIP   = Bool()
  val WPRI_1 = Bool()
  val SEIP   = Bool()
  val UEIP   = Bool()
  val MTIP   = Bool()
  val WPRI_2 = Bool()
  val STIP   = Bool()
  val UTIP   = Bool()
  val MSIP   = Bool()
  val WPRI_3 = Bool()
  val SSIP   = Bool()
  val USIP   = Bool()
}

class MieBundle(implicit val p: Parameters) extends Bundle with CPUParams {
  val WPRI_0 = UInt((xlen - 12).W)
  val MEIE   = Bool()
  val WPRI_1 = Bool()
  val SEIE   = Bool()
  val UEIE   = Bool()
  val MTIE   = Bool()
  val WPRI_2 = Bool()
  val STIE   = Bool()
  val UTIE   = Bool()
  val MSIE   = Bool()
  val WPRI_3 = Bool()
  val SSIE   = Bool()
  val USIE   = Bool()

  def := (that: => Data): Unit = {
    val mie = WireDefault(new MieBundle, that.asTypeOf(new MieBundle))
    MEIE := mie.MEIE; MSIE := mie.MSIE; MTIE := mie.MTIE
    if (extensions.contains('S')) { SEIE := mie.SEIE; SSIE := mie.SSIE; STIE := mie.STIE }
    if (extensions.contains('U')) { UEIE := mie.UEIE; USIE := mie.USIE; UTIE := mie.UTIE }
  }
}

class Sstatus(val mstatus: MstatusBundle)(implicit val p: Parameters) extends CPUParams {
  def := (that: => Data): Unit = {
    val smstatus = WireDefault(new MstatusBundle, that.asTypeOf(new MstatusBundle))
    smstatus.MPP := mstatus.MPP; smstatus.MPIE := mstatus.MPIE; smstatus.MIE := mstatus.MIE
    smstatus.TSR := mstatus.TSR
    mstatus := smstatus
  }
}

object Sstatus {
  import scala.language.implicitConversions
  implicit def getVal(x: Sstatus): UInt = {
    x.mstatus.SD ## (if (x.xlen == 32) 0.U(11.W) else (0.U((x.xlen - 35).W) ##
    x.mstatus.UXL ## 0.U(12.W))) ## x.mstatus.MXR ## x.mstatus.SUM ## 0.B ## x.mstatus.XS ##
    x.mstatus.FS ## 0.U(4.W) ## x.mstatus.SPP ## 0.U(2.W) ## x.mstatus.SPIE ## x.mstatus.UPIE ##
    0.U(2.W) ## x.mstatus.SIE ## x.mstatus.UIE
  }
}

class Sip(val mip: MipBundle)(implicit val p: Parameters) extends CPUParams {
  def := (that: => Data): Unit = {
    val smip = WireDefault(new MipBundle, that.asTypeOf(new MipBundle))
    mip.SSIP := smip.SSIP; mip.USIP := smip.USIP; mip.UEIP := smip.UEIP
  }
}

object Sip {
  import scala.language.implicitConversions
  implicit def getVal(x: Sip): UInt = {
    0.U((x.xlen - 10).W) ## x.mip.SEIP ## x.mip.UEIP ## 0.U(2.W) ## x.mip.STIP ## x.mip.UTIP ## 0.U(2.W) ## x.mip.SSIP ## x.mip.USIP
  }
}

class Sie(val mie: MieBundle)(implicit val p: Parameters) extends CPUParams {
  def := (that: => Data): Unit = {
    val smie = WireDefault(new MieBundle, that.asTypeOf(new MieBundle))
    smie.MEIE := mie.MEIE; smie.MSIE := mie.MSIE; smie.MTIE := mie.MTIE
    mie := smie
  }
}

object Sie {
  import scala.language.implicitConversions
  implicit def getVal(x: Sie): UInt = {
    0.U((x.xlen - 10).W) ## x.mie.SEIE ## x.mie.UEIE ## 0.U(2.W) ## x.mie.STIE ## x.mie.UTIE ## 0.U(2.W) ## x.mie.SSIE ## x.mie.USIE
  }
}

package cpu.privileged

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu._
import cpu.tools.YQBundle

class MstatusBundle(implicit val p: Parameters) extends Bundle with CPUParams {
  val SD     = Bool()
  val WPRI_0 = UInt((if (xlen == 32) 8 else xlen - 37).W)
  val SXL    = UInt((if (xlen == 32) 0 else 2).W)
  val UXL    = UInt((if (xlen == 32) 0 else 2).W)
  val WPRI_1 = UInt((if (xlen == 32) 0 else 9).W)
  val TSR    = Bool()
  val TW     = UInt(1.W)
  val TVM    = UInt(1.W)
  val MXR    = UInt(1.W)
  val SUM    = Bool()
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
    MXR := mstatus.MXR; SUM := mstatus.SUM; MPRV := mstatus.MPRV
    MPP := Mux(mstatus.MPP =/= 2.U, mstatus.MPP, MPP)
    SPP := mstatus.SPP; MPIE := mstatus.MPIE; SPIE := mstatus.SPIE; UPIE := mstatus.UPIE
    MIE := mstatus.MIE; SIE := mstatus.SIE; UIE := mstatus.UIE; TSR := mstatus.TSR
    FS := mstatus.FS; SD := mstatus.FS === "b11".U
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

  def := (that: => Data): Unit = {
    val mip = WireDefault(new MipBundle, that.asTypeOf(new MipBundle))
    if (ext('S')) { SEIP := mip.SEIP; SSIP := mip.SSIP; STIP := mip.STIP }
    if (ext('U')) { UEIP := mip.UEIP; USIP := mip.USIP; UTIP := mip.UTIP }
  }

  def apply(x: Int): Bool = asUInt(x)
  def apply(x: Int, y: Int): UInt = asUInt(x, y)
}

object MipBundle {
  import scala.language.implicitConversions
  implicit def mip2UInt(x: MipBundle): UInt = x.asUInt
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
    if (ext('S')) { SEIE := mie.SEIE; SSIE := mie.SSIE; STIE := mie.STIE }
    if (ext('U')) { UEIE := mie.UEIE; USIE := mie.USIE; UTIE := mie.UTIE }
  }

  def apply(x: Int): Bool = asUInt(x)
  def apply(x: Int, y: Int): UInt = asUInt(x, y)
  def apply(x: UInt): Bool = VecInit(Seq.tabulate(12)(asUInt(_)))(x)
}

class MidelegBundle(implicit val p: Parameters) extends Bundle with CPUParams {
  val WPRI_0 = UInt((xlen - 10).W)
  val SEI    = Bool()
  val UEI    = Bool()
  val WPRI_1 = UInt(2.W)
  val STI    = Bool()
  val UTI    = Bool()
  val WPRI_2 = UInt(2.W)
  val SSI    = Bool()
  val USI    = Bool()

  def := (that: => Data): Unit = {
    val mideleg = WireDefault(new MidelegBundle, that.asTypeOf(new MidelegBundle))
    if (ext('S')) { SEI := mideleg.SEI; SSI := mideleg.SSI; STI := mideleg.STI }
    if (ext('U')) { UEI := mideleg.UEI; USI := mideleg.USI; UTI := mideleg.UTI }
  }

  def apply(x: Int): Bool = asUInt(x)
  def apply(x: Int, y: Int): UInt = asUInt(x, y)
  def apply(x: UInt): Bool = VecInit(Seq.tabulate(12)(asUInt(_)))(x)
}

class MedelegBundle(implicit val p: Parameters) extends Bundle with CPUParams {
  val medeleg = UInt(xlen.W)
  def apply(x: Int): Bool = asUInt(x)
  def apply(x: Int, y: Int): UInt = asUInt(x, y)
  def apply(x: UInt): Bool = VecInit(Seq.tabulate(16)(asUInt(_)))(x)
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
  implicit def getVal(x: Sstatus): UInt =
    x.mstatus.SD ## (if (x.xlen == 32) 0.U(11.W) else (0.U((x.xlen - 35).W) ##
    x.mstatus.UXL ## 0.U(12.W))) ## x.mstatus.MXR ## x.mstatus.SUM ## 0.B ## x.mstatus.XS ##
    x.mstatus.FS ## 0.U(4.W) ## x.mstatus.SPP ## 0.U(2.W) ## x.mstatus.SPIE ## x.mstatus.UPIE ##
    0.U(2.W) ## x.mstatus.SIE ## x.mstatus.UIE
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

class SatpBundle extends Bundle {
  val mode = UInt(2.W)
  val PPN  = UInt(44.W)
}

class UseSatp(val satp: SatpBundle = null) {
  def asUInt: UInt = mode ## 0.U(16.W) ## satp.PPN
  def :=[T <: Data](that: T): Unit = {
    satp.mode := MuxLookup(that.asUInt(63, 60), 0.U, Seq(
      8.U -> "b10".U,
      9.U -> "b11".U
    ))
    satp.PPN := that.asUInt(43, 0)
  }
  def asTypeOf[T <: Data](that: T) = satp.asTypeOf(that)
  def mode: UInt = satp.mode(1) ## 0.U(2.W) ## satp.mode(0)
  def ppn: UInt = satp.PPN
}

object UseSatp {
  def apply(satp: SatpBundle): UseSatp = new UseSatp(satp)
  def apply(satp: UInt): UseSatp = {
    val wireSatp = WireDefault(0.U.asTypeOf(new SatpBundle))
    wireSatp.PPN  := satp(43, 0)
    wireSatp.mode := satp(63) ## satp(60)
    new UseSatp(wireSatp)
  }
  def apply(): UseSatp = new UseSatp(0.U.asTypeOf(new SatpBundle))
  import scala.language.implicitConversions
  implicit def Satp2UInt(x: UseSatp): UInt = x.asUInt
}

class CRMDBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val DATM = UInt(2.W)
  val DATF = UInt(2.W)
  val PG   = Bool()
  val DA   = Bool()
  val IE   = Bool()
  val PLV  = UInt(2.W)

  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    PLV  := _.PLV,
    IE   := _.IE,
    DA   := _.DA,
    PG   := _.PG,
    DATF := _.DATF,
    DATM := _.DATM
  )
}

class PRMDBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val PIE  = Bool()
  val PPLV = UInt(2.W)

  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    PPLV := _.PPLV,
    PIE  := _.PIE
  )
}

class ECFGBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val LIE = Vec(13, Bool())

  def TIE: Bool = LIE(11)
  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    LIE     := _.LIE,
    // LIE(10) := _.LIE(10) & 0.B FIXME: intentionally comment it for difftest
  )
}

class ESTATBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val EsubCode = UInt(9.W)
  val Ecode    = UInt(6.W)
  val RES1     = UInt(3.W)
  val IPIS     = Bool()
  val TIS      = Bool()
  val RES0     = UInt(1.W)
  val HWIS     = Vec(8, Bool())
  val SWIS     = Vec(2, Bool())

  def IS: Vec[Bool] = VecInit(this.asUInt(12, 0).asBools)
  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    SWIS     := _.SWIS,
    Ecode    := _.Ecode,
    EsubCode := _.EsubCode
  )
}

class LLBCTLBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val KLO   = Bool()
  val WCLLB = Bool()
  val ROLLB = Bool()

  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    that => ROLLB := that.ROLLB & ~that.WCLLB,
    KLO := _.KLO
  )
}

class DMWBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val VSEG = UInt(3.W)
  val RES2 = UInt(1.W)
  val PSEG = UInt(3.W)
  val RES1 = UInt(19.W)
  val MAT  = UInt(2.W)
  val PLV3 = Bool()
  val RES0 = UInt(2.W)
  val PLV0 = Bool()

  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    VSEG := _.VSEG,
    PSEG := _.PSEG,
    MAT  := _.MAT,
    PLV3 := _.PLV3,
    PLV0 := _.PLV0
  )
}

class TCFGBundle(implicit p: Parameters) extends YQBundle with CPUParams {
  val InitVal  = UInt(30.W)
  val Periodic = Bool()
  val En       = Bool()

  def := (that: => Data): Unit = that.asTypeOf(this).connect(
    InitVal  := _.InitVal,
    Periodic := _.Periodic,
    En       := _.En
  )
}

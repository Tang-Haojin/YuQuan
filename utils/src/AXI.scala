package utils

import chisel3._
import chisel3.util.ReadyValidIO
import chipsalliance.rocketchip.config._

// for simple single-direction communication
class LastVR(implicit val p: Parameters) extends Bundle with UtilsParams {
  val VALID = Input(Bool())
  val READY = Output(Bool())
}

class BASIC(implicit val p: Parameters) extends Bundle with UtilsParams {
  // Global signal
  val ACLK    = Input (Clock())
  val ARESETn = Input (Bool())
}

class AxiSlaveIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val basic   = new BASIC
  val channel = Flipped(new AXI_BUNDLE)
}

class AxiSelectIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val input = Flipped(new AXI_BUNDLE)
  val RamIO = new AXI_BUNDLE
  val MMIO  = new AXI_BUNDLE
}

class AXI_BUNDLE_A(implicit val p: Parameters) extends Bundle with UtilsParams {
  val id     = UInt(idlen.W)
  val addr   = UInt(alen.W)
  val len    = UInt(8.W)
  val size   = UInt(3.W)
  val burst  = UInt(2.W)
  val lock   = UInt((2*uselock).W)
  val cache  = UInt((4*usecache).W)
  val prot   = UInt((3*useprot).W)
  val qos    = UInt((4*useqos).W)
  val region = UInt((4*useregion).W)
  val user   = UInt(usrlen.W)
}

class AXI_BUNDLE_AW(implicit p: Parameters) extends ReadyValidIO(new AXI_BUNDLE_A)
class AXI_BUNDLE_AR(implicit p: Parameters) extends ReadyValidIO(new AXI_BUNDLE_A)

// Write data channel signals
class AXI_BUNDLE_W(implicit p: Parameters) extends ReadyValidIO(new Bundle {
  val id   = if (p(ISAXI3)) UInt(p(IDLEN).W) else null
  val data = UInt(p(XLEN).W)
  val strb = UInt((p(XLEN)/8).W)
  val last = Bool()
  val user = UInt(p(USRLEN).W)
})

// Write response channel signals
class AXI_BUNDLE_B(implicit p: Parameters) extends ReadyValidIO(new Bundle {
  val id   = UInt(p(IDLEN).W)
  val resp = UInt(2.W)
  val user = UInt(p(USRLEN).W)
})

// Read data channel signals
class AXI_BUNDLE_R(implicit p: Parameters) extends ReadyValidIO(new Bundle {
  val id   = UInt(p(IDLEN).W)
  val data = UInt(p(XLEN).W)
  val resp = UInt(2.W)
  val last = Bool()
  val user = UInt(p(USRLEN).W)
})

class AXI_BUNDLE(implicit val p: Parameters) extends Bundle with UtilsParams {
  val aw = new AXI_BUNDLE_AW
  val ar = new AXI_BUNDLE_AR
  val w  = new AXI_BUNDLE_W
  val b  = Flipped(new AXI_BUNDLE_B)
  val r  = Flipped(new AXI_BUNDLE_R)
}

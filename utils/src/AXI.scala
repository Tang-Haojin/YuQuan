package utils

import chisel3._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.diplomacy.ValName
import chisel3.util.Decoupled
import chisel3.util.Irrevocable

// for simple single-direction communication
class LastVR(implicit val p: Parameters) extends Bundle with UtilsParams {
  val VALID = Input(Bool())
  val READY = Output(Bool())
}

class BASIC(implicit val p: Parameters) extends Bundle with UtilsParams {
  // Global signal
  val ACLK     = Input (Clock())
  val ARESETn  = Input (Bool())
}

class AxiSlaveIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val basic   = new BASIC
  val channel = Flipped(new AXI_BUNDLE)
}

class AxiSelectIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val input   = Flipped(new AXI_BUNDLE)
  val RamIO   = new AXI_BUNDLE
  val MMIO    = new AXI_BUNDLE
}

class AXI_BUNDLE_A(implicit val p: Parameters) extends Bundle with UtilsParams {
  val id     = UInt(idlen.W)
  val addr   = UInt(alen.W)
  val len    = UInt(8.W)
  val size   = UInt(3.W)
  val burst  = UInt(2.W)
  val lock   = UInt(2.W)
  val cache  = UInt(4.W)
  val prot   = UInt(3.W)
  val qos    = UInt(4.W)
  val region = UInt(4.W)
  val user   = UInt(1.W)
}

class AXI_BUNDLE_AW(implicit p: Parameters) extends AXI_BUNDLE_A
class AXI_BUNDLE_AR(implicit p: Parameters) extends AXI_BUNDLE_A

// Write data channel signals
class AXI_BUNDLE_W(implicit val p: Parameters) extends Bundle with UtilsParams {
  val data    = UInt(xlen.W)
  val strb    = UInt((xlen / 8).W)
  val last    = Bool()
  val user    = UInt(1.W)
}

// Write response channel signals
class AXI_BUNDLE_B(implicit val p: Parameters) extends Bundle with UtilsParams {
  val id      = UInt(idlen.W)
  val resp    = UInt(2.W)
  val user    = UInt(1.W)
}
// Read data channel signals
class AXI_BUNDLE_R(implicit val p: Parameters) extends Bundle with UtilsParams {
  val id      = Input (UInt(idlen.W))
  val data    = Input (UInt(xlen.W))
  val resp    = Input (UInt(2.W))
  val last    = Input (Bool())
  val user    = Input (UInt(1.W))
}

class AXI_BUNDLE(implicit val p: Parameters, valName: ValName) extends Bundle with UtilsParams {
  val aw = Irrevocable(new AXI_BUNDLE_AW)
  val ar = Irrevocable(new AXI_BUNDLE_AR)
  val w  = Irrevocable(new AXI_BUNDLE_W)
  val b  = Flipped(Irrevocable(new AXI_BUNDLE_B))
  val r  = Flipped(Irrevocable(new AXI_BUNDLE_R))
}

abstract trait AXI_RENAME extends Bundle {
  protected def rename: Unit = 
    println(this.getElements(0).computeName(None, None))
}

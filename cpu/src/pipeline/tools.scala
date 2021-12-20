package cpu.pipeline

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import chipsalliance.rocketchip.config._

import utils._

import cpu.instruction._
import cpu.component._
import cpu.component.mmu._
import cpu.tools._
import cpu.cache._
import cpu._

// EX
object ExitReasons {
  val reasons = Enum(3)
  val non::trap::inv::Nil = reasons
}

class EXOutput(implicit p: Parameters) extends YQBundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(xlen.W))
  val isWcsr  = Output(Bool())
  val wcsr    = Output(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val csrData = Output(Vec(RegConf.writeCsrsPort, UInt(xlen.W)))
  val isMem   = Output(Bool())
  val isLd    = Output(Bool())
  val addr    = Output(UInt(valen.W))
  val mask    = Output(UInt(3.W))
  val retire  = Output(Bool())
  val priv    = Output(UInt(2.W))
  val isPriv  = Output(Bool())
  val isSatp  = Output(Bool())
  val except  = Output(Bool())
  val cause   = Output(UInt(4.W))
  val fshTLB  = if (ext('S')) Output(Bool()) else null
  val pc      = Output(UInt(valen.W))
  val debug   =
    if (Debug) new YQBundle {
      val exit = Output(UInt(3.W))
      val rcsr = Output(UInt(12.W))
      val intr = Output(Bool())
      val rvc  = Output(Bool())
    } else null
}

// ID
object ExecSpecials {
  val specials: List[UInt] = Enum(17)
  val norm::ld::st::trap::inv::word::zicsr::mret::exception::mu::msu::ecall::ebreak::sret::fencei::amo::sfence::Nil = specials
}

object InstrTypes {
  val instrTypes: List[UInt] = Enum(18) /*(0 until 18).map(x => (1 << x).U(18.W)).toList*/
  val i::u::s::r::j::b::c::err::clsp::cssp::cldst::cj::cni::cb::c540::clui::caddi4::cinv::Nil = instrTypes 
}

object NumTypes {
  val numtypes: List[UInt] = Enum(15)
  val non::rs1::rs2::imm::four::pc::csr::rs1c::rs2c::rs1p::rs2p::rd1c::rd1p::x2::two::Nil = numtypes
}

case class RVInstr()(implicit val p: Parameters) extends CPUParams {
  val table: Array[(BitPat, List[UInt])] = (
    RVI().table ++ Zicsr().table ++ Privileged().table ++ (if (!isZmb) Zifencei().table else Nil) ++
    (if (ext('M')) RVM().table else Nil) ++ (if (ext('A')) RVA().table else Nil) ++
    (if (ext('C')) RVC().table else Nil)
  ).toArray
}

object RVInstrDecoder {
  def apply(instr: UInt)(implicit p: Parameters): Seq[UInt] = {
    val table = RVInstr().table
    val splitTable = Seq.tabulate(table.head._2.length)(x => table.map(y => (y._1, BitPat(y._2(x)))))
    val decodeSeq = splitTable zip (Seq(InstrTypes.err) ++ Seq.fill(4)(NumTypes.non) ++ Seq(cpu.component.Operators.nop, 0.B, ExecSpecials.inv)).map(BitPat(_))
    decodeSeq.map(x => decoder.qmc(instr, TruthTable(x._1, x._2)))
  }
}

class IDOutput(implicit p: Parameters) extends YQBundle {
  val rd      = Output(UInt(5.W))
  val isWcsr  = Output(Bool())
  val wcsr    = Output(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val num     = Output(Vec(4, UInt(xlen.W)))
  val op1_2   = Output(UInt(Operators.quantity.W))
  val op1_3   = Output(UInt(Operators.quantity.W))
  val special = Output(UInt(5.W))
  val retire  = Output(Bool())
  val priv    = Output(UInt(2.W))
  val isPriv  = Output(Bool())
  val isSatp  = Output(Bool())
  val except  = Output(Bool())
  val cause   = Output(UInt(4.W))
  val pc      = Output(UInt(valen.W))
  val debug   =
    if (Debug) new YQBundle {
      val rcsr = Output(UInt(12.W))
      val intr = Output(Bool())
      val priv = Output(UInt(2.W))
      val rvc  = Output(Bool())
    } else null
}

class IDIO(implicit p: Parameters) extends YQBundle {
  val output      = new IDOutput
  val gprsR       = Flipped(new GPRsR)
  val csrsR       = Flipped(new cpu.privileged.CSRsR)
  val lastVR      = new LastVR
  val nextVR      = Flipped(new LastVR)
  val input       = Flipped(new IFOutput)
  val jmpBch      = Output(Bool())
  val jbAddr      = Output(UInt(valen.W))
  val isWait      = Input (Bool())
  val currentPriv = Input (UInt(2.W))
  val isAmo       = Output(Bool())
  val mtip        = Input (Bool())
  val msip        = Input (Bool())
  val revAmo      = Input (Bool())
}

class EXIO(implicit p: Parameters) extends YQBundle {
  val input  = Flipped(new IDOutput)
  val lastVR = new LastVR
  val nextVR = Flipped(new LastVR)
  val output = new EXOutput
  val invIch = Irrevocable(UInt(0.W))
  val wbDch  = Irrevocable(UInt(0.W))
  val seip   = Input (Bool())
  val ueip   = Input (Bool())
}

class MEMIO(implicit p: Parameters) extends YQBundle {
  val dmmu   = Flipped(new PipelineIO)
  val lastVR = new LastVR
  val nextVR = Flipped(new LastVR)
  val input  = Flipped(new EXOutput)
  val output = new MEMOutput
}

// IF
class IFOutput(implicit p: Parameters) extends YQBundle {
  val instr      = Output(UInt(32.W))
  val instrCode  = Output(UInt(7.W))
  val rs         = Output(Vec(2, UInt(5.W)))
  val rd         = Output(UInt(5.W))
  val pc         = Output(UInt(valen.W))
  val except     = Output(Bool())
  val cause      = Output(UInt(4.W))
  val crossCache = Output(Bool())
}

// MEM
class MEMOutput(implicit p: Parameters) extends YQBundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(xlen.W))
  val isWcsr  = Output(Bool())
  val wcsr    = Output(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val csrData = Output(Vec(RegConf.writeCsrsPort, UInt(xlen.W)))
  val retire  = Output(Bool())
  val priv    = Output(UInt(2.W))
  val isPriv  = Output(Bool())
  val isSatp  = Output(Bool())
  val except  = Output(Bool())
  val debug   =
    if (Debug) new YQBundle {
      val exit = Output(UInt(3.W))
      val pc   = Output(UInt(valen.W))
      val rcsr = Output(UInt(12.W))
      val mmio = Output(Bool())
      val intr = Output(Bool())
      val rvc  = Output(Bool())
    } else null
}

// Bypass
class RdVal(implicit p: Parameters) extends YQBundle {
  val valid = Input(Bool())
  val index = Input(UInt(5.W))
  val value = Input(UInt(xlen.W))
}

object ExceptionCode extends Enumeration {
  import chisel3.internal.firrtl.Width
  val usi,ssi,hsi,msi,uti,sti,hti,mti,uei,sei,hei,mei = Value
  implicit class ExceptionCodeImplicit(x: Value) {
    def U(width: chisel3.internal.firrtl.Width): UInt = x.id.U(width)
  }
  import scala.language.implicitConversions
  implicit def ExceptionCodeToInt(x: Value): Int = x.id
}

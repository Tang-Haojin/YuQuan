package cpu.pipeline

import chisel3._
import chisel3.util._
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
  val fshTLB  = if (extensions.contains('S')) Output(Bool()) else null
  val debug   =
    if (Debug) new YQBundle {
      val exit  = Output(UInt(3.W))
      val pc    = Output(UInt(valen.W))
      val rcsr  = Output(UInt(12.W))
      val clint = Output(Bool())
      val intr  = Output(Bool())
    } else null
}

// ID
object ExecSpecials {
  val specials = Enum(20)
  val non::ld::st::jump::jalr::branch::trap::inv::word::csr::mret::exception::mu::msu::ecall::ebreak::sret::fencei::amo::sfence::Nil = specials
}

object InstrTypes { val i::u::s::r::j::b::c::Nil = Enum(7) }

object NumTypes {
  val numtypes = Enum(8)
  val non::rs1::rs2::imm::four::pc::csr::tmp::Nil = numtypes
}

case class RVInstr()(implicit val p: Parameters) extends CPUParams {
  val table = RVI().table ++ Zicsr().table ++ Privileged().table ++ (if (extensions.contains('M')) RVM().table else Nil) ++
              Zifencei().table ++ (if (extensions.contains('A')) RVA().table else Nil)
}

class IDOutput(implicit p: Parameters) extends YQBundle {
  val rd      = Output(UInt(5.W))
  val wcsr    = Output(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val num     = Output(Vec(4, UInt(xlen.W)))
  val op1_2   = Output(UInt(AluTypeWidth.W))
  val op1_3   = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(5.W))
  val retire  = Output(Bool())
  val priv    = Output(UInt(2.W))
  val isPriv  = Output(Bool())
  val isSatp  = Output(Bool())
  val except  = Output(Bool())
  val cause   = Output(UInt(4.W))
  val debug   =
    if (Debug) new YQBundle {
      val pc    = Output(UInt(valen.W))
      val rcsr  = Output(UInt(12.W))
      val clint = Output(Bool())
      val intr  = Output(Bool())
      val priv  = Output(UInt(2.W))
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
  val ifIsPriv    = Output(Bool())
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
  val instr  = Output(UInt(32.W))
  val pc     = Output(UInt(valen.W))
  val except = Output(Bool())
  val cause  = Output(UInt(4.W))
}

// MEM
class MEMOutput(implicit p: Parameters) extends YQBundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(xlen.W))
  val wcsr    = Output(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val csrData = Output(Vec(RegConf.writeCsrsPort, UInt(xlen.W)))
  val retire  = Output(Bool())
  val priv    = Output(UInt(2.W))
  val isPriv  = Output(Bool())
  val isSatp  = Output(Bool())
  val debug   =
    if (Debug) new YQBundle {
      val exit  = Output(UInt(3.W))
      val pc    = Output(UInt(valen.W))
      val rcsr  = Output(UInt(12.W))
      val mmio  = Output(Bool())
      val clint = Output(Bool())
      val intr  = Output(Bool())
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

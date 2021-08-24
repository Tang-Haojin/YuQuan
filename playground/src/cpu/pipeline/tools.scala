package cpu.pipeline

import chisel3._
import chisel3.util._

import tools._

import cpu.config.Debug._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.instruction._
import cpu.component._

// EX
object ExitReasons {
  val reasons = Enum(3)
  val non::trap::inv::Nil = reasons
}

class EXOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(XLEN.W))
  val wcsr    = Output(Vec(writeCsrsPort, UInt(12.W)))
  val csrData = Output(Vec(writeCsrsPort, UInt(XLEN.W)))
  val isMem   = Output(Bool())
  val isLd    = Output(Bool())
  val addr    = Output(UInt(XLEN.W))
  val mask    = Output(UInt(3.W))
  val debug   =
    if (Debug) new Bundle {
      val exit  = Output(UInt(3.W))
      val pc    = Output(UInt(XLEN.W))
    } else null
}

// ID
object ExecSpecials {
  val specials = Enum(12)
  val non::ld::st::jump::jalr::branch::trap::inv::word::csr::mret::int::Nil = specials
}

object InstrTypes { val i::u::s::r::j::b::c::Nil = Enum(7) }

object NumTypes {
  val numtypes = Enum(8)
  val non::rs1::rs2::imm::four::pc::fun3::csr::Nil = numtypes
}

object RVInstr {
  val table = RVI.table ++ Zicsr.table ++ Privileged.table ++ (if (HasRVM) RVM.table else Nil)
}

class IDOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val wcsr    = Output(Vec(writeCsrsPort, UInt(12.W)))
  val num     = Output(Vec(4, UInt(XLEN.W)))
  val op1_2   = Output(UInt(AluTypeWidth.W))
  val op1_3   = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(5.W))
  val debug   =
    if (Debug) new Bundle {
      val pc = Output(UInt(XLEN.W))
    } else null
}

class IDIO extends Bundle {
  val output    = new IDOutput
  val gprsR     = Flipped(new GPRsR)
  val csrsR     = Flipped(new cpu.privileged.CSRsR)
  val lastVR    = new LastVR
  val nextVR    = Flipped(new LastVR)
  val input     = Flipped(new IFOutput)
  val jmpBch    = Output(Bool())
  val jbAddr    = Output(UInt(XLEN.W))
  val isWait    = Input (Bool())
}

// IF
class IFOutput extends Bundle {
  val instr = Output(UInt(32.W))
  val pc    = Output(UInt(XLEN.W))
}

// MEM
class MEMOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(XLEN.W))
  val wcsr    = Output(Vec(writeCsrsPort, UInt(12.W)))
  val csrData = Output(Vec(writeCsrsPort, UInt(XLEN.W)))
  val debug   =
    if (Debug) new Bundle {
      val exit  = Output(UInt(3.W))
      val pc = Output(UInt(XLEN.W))
    } else null
}

// Bypass
class RdVal extends Bundle {
  val index = Input(UInt(5.W))
  val value = Input(UInt(XLEN.W))
}

// BypassCsr
class CsrVal extends Bundle {
  val wcsr  = Input(Vec(writeCsrsPort, UInt(12.W)))
  val value = Input(Vec(writeCsrsPort, UInt(XLEN.W)))
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

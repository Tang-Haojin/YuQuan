package cpu.pipeline

import chisel3._
import chisel3.util._

import cpu.config.Debug._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.instruction._

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
  val num1    = Output(UInt(XLEN.W))
  val num2    = Output(UInt(XLEN.W))
  val num3    = Output(UInt(XLEN.W))
  val num4    = Output(UInt(XLEN.W))
  val op1_2   = Output(UInt(AluTypeWidth.W))
  val op1_3   = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(5.W))
  val debug   =
    if (Debug) new Bundle {
      val pc = Output(UInt(XLEN.W))
    } else null
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

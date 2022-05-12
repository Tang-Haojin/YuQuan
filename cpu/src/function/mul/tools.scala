package cpu.function.mul

import chisel3._
import chisel3.util._
import cpu.tools._
import chipsalliance.rocketchip.config.Parameters

class CSAIO(implicit p: Parameters) extends YQBundle {
  val input  = Input (Vec(3, UInt((2 * xlen).W)))
  val output = Output(Vec(2, UInt((2 * xlen).W)))
}

class _42_CompressorIO(implicit p: Parameters) extends YQBundle {
  val input  = Input (Vec(4, UInt((2 * xlen).W)))
  val output = Output(Vec(2, UInt((2 * xlen).W)))
}

class WalImprovedIO(implicit p: Parameters) extends YQBundle {
  val input  = Input (Vec(18, UInt((2 * xlen).W)))
  val output = Output(Vec(2 , UInt((2 * xlen).W)))
}

class BoothEncIO extends Bundle {
  val code = Input (UInt(3.W))
  val neg  = Output(Bool())
  val zero = Output(Bool())
  val one  = Output(Bool())
  val two  = Output(Bool())
}

class MulTopIn(implicit p: Parameters) extends YQBundle {
  val data = Output(Vec(2, UInt(xlen.W)))
  val sign = Output(Vec(2, Bool()))
}

class MulTopIO(implicit p: Parameters) extends YQBundle {
  val input  = Flipped(Decoupled(new MulTopIn))
  val output = Decoupled(UInt((2 * xlen).W))
}

class BoothSextIO(entries: Int, size: Int) extends Bundle {
  val op_0   = Input (UInt(size.W))
  val sign   = Input (Bool())
  val input  = Input (Vec(entries, UInt(3.W)))
  val output = Output(Vec(entries, UInt((size + 1 + 4).W)))
}

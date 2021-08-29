package cpu.function.mul

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import cpu.tools._

class CSAIO(length: Int = 128)(implicit p: Parameters) extends YQBundle {
  val input  = Input (Vec(3, UInt(length.W)))
  val output = Output(Vec(2, UInt(length.W)))
}

class _42_CompressorIO(length: Int = 128)(implicit p: Parameters) extends YQBundle {
  val input  = Input (Vec(4, UInt(length.W)))
  val cin    = Input (Bool())
  val output = Output(Vec(2, UInt(length.W)))
  val cout   = Output(Bool())
}

class Wallace_ImprovedIO(length: Int = 128)(implicit p: Parameters) extends YQBundle {
  val input  = Input (Vec(18, UInt(length.W)))
  val output = Output(Vec(2 , UInt(length.W)))
}

class BoothEncIO(implicit p: Parameters) extends YQBundle {
  val code = Input (UInt(3.W))
  val neg  = Output(Bool())
  val zero = Output(Bool())
  val one  = Output(Bool())
  val two  = Output(Bool())
}

class MultiTopIn(implicit p: Parameters) extends YQBundle {
  val data = Output(Vec(2, UInt(64.W)))
  val sign = Output(Vec(2, Bool()))
}

class MultiTopIO(implicit p: Parameters) extends YQBundle {
  val input  = Flipped(Decoupled(new MultiTopIn))
  val output = Decoupled(UInt(128.W))
}

class BoothSextIO(entries: Int = 17, size: Int = 64)(implicit p: Parameters) extends YQBundle {
  val op_0   = Input (UInt(size.W))
  val sign   = Input (Bool())
  val input  = Input (Vec(entries, UInt(3.W)))
  val output = Output(Vec(entries, UInt((size + 1 + 4).W)))
}

package cpu.function.mul

import chisel3._
import chisel3.util._

class CSAIO(length: Int = 128) extends Bundle {
  val input  = Input (Vec(3, UInt(length.W)))
  val output = Output(Vec(2, UInt(length.W)))
}

class _42_CompressorIO(length: Int = 128) extends Bundle {
  val input  = Input (Vec(4, UInt(length.W)))
  val output = Output(Vec(2, UInt(length.W)))
}

class WalImprovedIO(length: Int = 128) extends Bundle {
  val input  = Input (Vec(18, UInt(length.W)))
  val output = Output(Vec(2 , UInt(length.W)))
}

class BoothEncIO extends Bundle {
  val code = Input (UInt(3.W))
  val neg  = Output(Bool())
  val zero = Output(Bool())
  val one  = Output(Bool())
  val two  = Output(Bool())
}

class MulTopIn extends Bundle {
  val data = Output(Vec(2, UInt(64.W)))
  val sign = Output(Vec(2, Bool()))
}

class MulTopIO extends Bundle {
  val input  = Flipped(Decoupled(new MulTopIn))
  val output = Decoupled(UInt(128.W))
}

class BoothSextIO(entries: Int = 17, size: Int = 64) extends Bundle {
  val op_0   = Input (UInt(size.W))
  val sign   = Input (Bool())
  val input  = Input (Vec(entries, UInt(3.W)))
  val output = Output(Vec(entries, UInt((size + 1 + 4).W)))
}

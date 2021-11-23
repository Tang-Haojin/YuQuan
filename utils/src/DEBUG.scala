package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

class DEBUG(implicit val p: Parameters) extends Bundle with UtilsParams {
  val exit     = Output(UInt(3.W))
  val wbPC     = Output(UInt(xlen.W))
  val wbValid  = Output(Bool())
  val wbRd     = Output(UInt(5.W))
  val wbRcsr   = Output(UInt(12.W))
  val wbMMIO   = Output(Bool())
  val wbIntr   = Output(Bool())
  val wbRvc    = Output(Bool())
  val gprs     = Output(Vec(32, UInt(xlen.W)))
  val priv     = Output(UInt(2.W))
  val mstatus  = Output(UInt(xlen.W))
  val mepc     = Output(UInt(xlen.W))
  val sepc     = Output(UInt(xlen.W))
  val mtvec    = Output(UInt(xlen.W))
  val stvec    = Output(UInt(xlen.W))
  val mcause   = Output(UInt(xlen.W))
  val scause   = Output(UInt(xlen.W))
  val mtval    = Output(UInt(xlen.W))
  val stval    = Output(UInt(xlen.W))
  val mie      = Output(UInt(xlen.W))
  val mscratch = Output(UInt(xlen.W))
}

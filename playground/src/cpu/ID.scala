package cpu

import chisel3._
import chisel3.util._

import cpu.axi.LastVR

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.ExecSpecials._
import cpu.InstrTypes._

import meta.Booleans._
import meta.PreProc._
import cpu.axi.BASIC

object ExecSpecials {
  val specials = Enum(5)
  val no::ld::st::jump::branch::auipc::Nil = specials
}

object InstrTypes {
  val instrtypes = Enum(6)
  val i::u::s::r::j::b::Nil = instrtypes
}

class IDOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val num1    = Output(UInt(XLEN.W))
  val num2    = Output(UInt(XLEN.W))
  val imm     = Output(UInt(XLEN.W))
  val op      = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(3.W))
}

// instruction decoding module
class ID extends RawModule {
  val io = IO(new Bundle {
    val idBasic = new BASIC              // connected
    val idData = new IDOutput            // connected
    val idGprsR = new GPRsR              // connected
    val idLastVR   = new LastVR          // connected
    val idNextVR   = Flipped(new LastVR) // connected
    val instr   = Input (UInt(XLEN.W))   // connected
  })

  withClockAndReset(io.idBasic.ACLK, ~io.idBasic.ARESETn) {
    val NVALID  = RegInit(0.B)
    val LREADY  = RegInit(0.B)

    val rd      = RegInit(0.U(5.W))
    val num1    = RegInit(0.U(XLEN.W))
    val num2    = RegInit(0.U(XLEN.W))
    val imm     = RegInit(0.U(XLEN.W))
    val op      = RegInit(0.U(AluTypeWidth.W))
    val special = RegInit(0.U(3.W))

    val wireRd      = UInt(5.W)
    val wireNum1    = UInt(XLEN.W)
    val wireNum2    = UInt(XLEN.W)
    val wireOp      = UInt(AluTypeWidth.W)
    val wireImm     = UInt(XLEN.W)
    val wireSpecial = UInt(3.W)

    val wireType    = UInt(3.W)

    io.idNextVR.VALID  := NVALID
    io.idLastVR.READY  := LREADY
    io.idData.rd      := rd
    io.idData.num1    := num1
    io.idData.num2    := num2
    io.idData.imm     := imm
    io.idData.op      := op
    io.idData.special := special

    wireRd      := io.instr(11, 7)
    wireNum1    := 0.U
    wireNum2    := 0.U
    wireOp      := 0.U
    wireImm     := 0.U
    wireSpecial := 0.U

    wireType := 7.U

    switch(io.instr(6, 2)) {
      is("b00000".U) { // IDEX (0b00000, I, load)
        wireType    := i
        wireSpecial := ld
      }
      is("b00100".U) { // IDEX (0b00100, I, computei)
        wireType    := i
        wireSpecial := no
      }
      is("b00101".U) { // IDEX (0b00101, U, auipc)
        wireType    := u
        wireSpecial := auipc
      }
      is("b01000".U) { // IDEX (0b01000, S, store)
        wireType    := s
        wireSpecial := st
      }
      is("b01100".U) { // IDEX (0b01100, R, compute)
        wireType := r
      }
      is("b01101".U) { // IDEX (0b01101, U, lui)
        wireType := u
      }
      is("b01110".U) { // IDEX (0b01110, R, computew)
        wireType := r
      }
      is("b11000".U) { // IDEX (0b11000, B, branch)
        wireType := b
      }
      is("b11001".U) { // IDEX (0b11001, I, jalr)
        wireType := i
      }
      is("b11011".U) { // IDEX (0b11011, J, jal)
        wireType := j
      }
      is("b11100".U) { // IDEX (0b11100, I, raise)
        wireType := i
      }
    }

IF[RV64I] {

    switch(io.instr(6, 2)) {
      is("b00110".U) { // IDEX (0b00110, I, computeiw)
        wireType    := i
        wireSpecial := no
      }
    }

}

    switch(wireType) {
      is(i) {
        wireImm := Cat(Fill(XLEN - 12, io.instr(31)), io.instr(31, 20))
      }
      is(s) {
        wireImm := Cat(Seq(
          Fill(XLEN - 12, io.instr(31)), 
          io.instr(31, 25), 
          io.instr(11, 7)
        ))
      }
      is(b) {
        wireImm := Cat(Seq(
          Fill(XLEN - 12, io.instr(31)),
          io.instr(7),
          io.instr(30, 25),
          io.instr(11, 8),
          0.U(1.W)
        ))
      }
      is(u) {
        wireImm := Cat(Seq(
          Fill(XLEN - 32, io.instr(31)),
          io.instr(31, 12),
          0.U(12.W)
        ))
      }
      is(j) {
        wireImm := Cat(Seq(
          Fill(XLEN - 20, io.instr(31)),
          io.instr(19, 12),
          io.instr(20),
          io.instr(30, 21),
          0.U(1.W)
        ))
      }
    }

    when(io.idLastVR.VALID && io.idLastVR.READY) { // let's start working
      LREADY := 0.B
      num1   := io.instr(19, 15)
      rd     := io.instr(11,  7)
    }
  }
}
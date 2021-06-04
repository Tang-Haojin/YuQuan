package cpu

import chisel3._
import chisel3.util._

import meta.Booleans._
import meta.PreProc._

import cpu.axi.LastVR

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.ExecSpecials._
import cpu.InstrTypes._
import cpu.axi.BASIC


object ExecSpecials {
  val specials = Enum(5)
  val no::ld::st::jump::branch::auipc::Nil = specials
}

object InstrTypes {
  val instrtypes = Enum(6)
  val i::u::s::r::j::b::Nil = instrtypes
}

object NumTypes {
  val numtypes = Enum(4)
  val non::rs1::rs2::imm::Nil = numtypes
}

object IDLocal {
  val XLEN = cpu.config.GeneralConfig.XLEN
}

class IDOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val num1    = Output(UInt(XLEN.W))
  val num2    = Output(UInt(XLEN.W))
  val num3    = Output(UInt(XLEN.W))
  val op      = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(3.W))
}

// instruction decoding module
class ID extends RawModule {
  val io = IO(new Bundle {
    val idBasic = new BASIC              // connected
    val idData = new IDOutput            // connected
    val idGprsR = Flipped(new GPRsR)     // connected
    val idLastVR   = new LastVR          // connected
    val idNextVR   = Flipped(new LastVR) // connected
    val instr   = Input (UInt(XLEN.W))   // connected
  })

  withClockAndReset(io.idBasic.ACLK, ~io.idBasic.ARESETn) {
    val NVALID  = RegInit(0.B)
    val LREADY  = RegInit(0.B)

    val rd      = RegInit(0.U(5.W))
    val (num1, num2, num3) = (RegInit(0.U(XLEN.W)), RegInit(0.U(XLEN.W)), RegInit(0.U(XLEN.W)))
    val op      = RegInit(0.U(AluTypeWidth.W))
    val special = RegInit(0.U(3.W))

    val wireRd      = Wire(UInt(5.W))
    val (
      wireNum1, wireNum2, wireNum3, wireImm
    ) = (
      Wire(UInt(XLEN.W)), Wire(UInt(XLEN.W)), Wire(UInt(XLEN.W)), Wire(UInt(XLEN.W))
    )
    val wireOp      = Wire(UInt(AluTypeWidth.W))
    val wireSpecial = Wire(UInt(3.W))
    val (wireRs1, wireRs2) = (Wire(UInt(5.W)), Wire(UInt(5.W)))
    val (wireDataRs1, wireDataRs2) = (Wire(UInt(XLEN.W)), Wire(UInt(XLEN.W)))
    val wireFunt3   = Wire(UInt(3.W))

    val wireType    = Wire(UInt(3.W))

    io.idNextVR.VALID := NVALID
    io.idLastVR.READY := LREADY
    io.idData.rd      := rd
    io.idData.num1    := num1
    io.idData.num2    := num2
    io.idData.num3    := num3
    io.idData.op      := op
    io.idData.special := special

    wireRd              := io.instr(11, 7)
    wireRs1             := io.instr(19, 15)
    wireRs2             := io.instr(24, 20)
    io.idGprsR.raddr(0) := wireRs1
    io.idGprsR.raddr(1) := wireRs2
    wireDataRs1         := io.idGprsR.rdata(0)
    wireDataRs2         := io.idGprsR.rdata(1)
    wireFunt3           := io.instr(14, 12)
    wireNum1            := 0.U
    wireNum2            := 0.U
    wireNum3            := 0.U
    wireImm             := 0.U
    wireSpecial         := 0.U

    wireType := 7.U

    val decoded = ListLookup(
      io.instr,
      List(7.U, 0.U, 0.U, 0.U, 0.U, 0.B),
      RVI.table
    )

    val instrValid = (decoded(0) =/= 7.U)

    wireOp := decoded(0)

    val numList = List((wireNum1, decoded(1)), (wireNum2, decoded(2)), (wireNum3, decoded(3)))
    for (num <- numList) {
      switch(num._2) {
        is(NumTypes.rs1) {
          num._1 := wireDataRs1
        }
        is(NumTypes.rs2) {
          num._1 := wireDataRs2
        }
        is(NumTypes.imm) {
          num._1 := wireImm
        }
      }
    }

    switch(decoded(0)) {
      is(i) {
        wireImm := Cat(Fill(XLEN - 12, io.instr(31)), io.instr(31, 20))
      }
    }

    when(io.idNextVR.VALID && io.idNextVR.READY) { // ready to trans instr to the next level
      NVALID  := 0.B
      LREADY  := 1.B
    }.elsewhen(io.idLastVR.VALID && io.idLastVR.READY && instrValid) { // let's start working
      NVALID  := 1.B
      LREADY  := 0.B
      rd      := wireRd
      num1    := wireNum1
      num2    := wireNum2
      num3    := wireNum3
      op      := wireOp
      special := wireSpecial
    }
  }
}
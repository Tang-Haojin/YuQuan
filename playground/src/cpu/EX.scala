package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

import meta.Booleans._
import meta.PreProc._

class EXOutput extends Bundle {
  val rd     = Output(UInt(5.W))
  val data   = Output(UInt(XLEN.W))
  val isJump = Output(Bool())
  val isMem  = Output(Bool())
  val addr   = Output(UInt(XLEN.W))
}

class EX extends RawModule {
  val io = IO(new Bundle {
    val exBasic = new BASIC             // connected
    val input   = Flipped(new IDOutput) // connected
    val lastVR  = new LastVR            // connected
    val nextVR  = Flipped(new LastVR)   // connected
    val output  = new EXOutput
  })

  withClockAndReset(io.exBasic.ACLK, ~io.exBasic.ARESETn) {
    val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID
    val LREADY = RegInit(0.B); io.lastVR.READY := LREADY

    val rd     = RegInit(0.U(5.W))
    val data   = RegInit(0.U(XLEN.W))
    val isJump = RegInit(0.B)
    val isMem  = RegInit(0.B)
    val addr   = RegInit(0.U(XLEN.W))

    val wireRd     = Wire(UInt(5.W)); wireRd := io.input.rd
    val wireData   = Wire(UInt(XLEN.W))
    val wireIsJump = WireDefault(0.B)
    val wireIsMem  = WireDefault(0.B)
    val wireAddr   = WireDefault(0.U(XLEN.W))

    io.output.rd     := rd
    io.output.data   := data
    io.output.isJump := isJump
    io.output.isMem  := isMem
    io.output.addr   := addr

    val alu = Module(new ALU)
    
    wireData := alu.res.asUInt
    alu.a    := io.input.num1.asSInt
    alu.b    := io.input.num2.asSInt
    alu.op   := io.input.op

    when(io.nextVR.VALID && io.nextVR.READY) { // ready to trans result to the next level
      NVALID := 0.B
      LREADY := 1.B
    }.elsewhen(io.lastVR.VALID && io.lastVR.READY) { // let's start working
      NVALID := 1.B
      LREADY := 0.B
      rd     := wireRd
      data   := wireData
      isJump := wireIsJump
      isMem  := wireIsMem
      addr   := wireAddr
    }
  }
}

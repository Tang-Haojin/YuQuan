package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import cpu.ExecSpecials._

object ExitReasons {
  val reasons = Enum(3)
  val non::trap::inv::Nil = reasons
}

class EXOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val data    = Output(UInt(XLEN.W))
  val wcsr    = Output(UInt(12.W))
  val csrData = Output(UInt(XLEN.W))
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

class EX extends Module {
  val io = IO(new Bundle {
    val input  = Flipped(new IDOutput)
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val output = new EXOutput
  })

  val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID

  val rd      = RegInit(0.U(5.W))
  val data    = RegInit(0.U(XLEN.W))
  val wcsr    = RegInit(0xFFF.U(12.W))
  val csrData = RegInit(0.U(XLEN.W))
  val isMem   = RegInit(0.B)
  val isLd    = RegInit(0.B)
  val addr    = RegInit(0.U(XLEN.W))
  val mask    = RegInit(0.U(3.W))
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null

  val wireRd    = Wire(UInt(5.W))
  val wireData  = Wire(UInt(XLEN.W))
  val wireIsMem = Wire(Bool())
  val wireIsLd  = Wire(Bool())
  val wireAddr  = Wire(UInt(XLEN.W));
  val wireMask  = Wire(UInt(3.W))
  val wireExit  = if (Debug) Wire(UInt(3.W)) else null

  wireRd    := io.input.rd
  wireIsMem := (io.input.special === ld || io.input.special === st)
  wireIsLd  := (io.input.special === ld)
  wireAddr  := io.input.num3 + io.input.num4
  wireMask  := io.input.op1_3
  if (Debug) wireExit := ExitReasons.non

  io.output.rd      := rd
  io.output.data    := data
  io.output.wcsr    := wcsr
  io.output.csrData := csrData
  io.output.isMem   := isMem
  io.output.isLd    := isLd
  io.output.addr    := addr
  io.output.mask    := mask

  val alu1_2 = Module(new ALU)

  wireData  := alu1_2.io.res.asUInt
  when(io.input.special === word) {
    wireData := Cat(Fill(32, alu1_2.io.res(31)), alu1_2.io.res(31, 0))
  }
  alu1_2.io.a  := io.input.num1.asSInt
  alu1_2.io.b  := io.input.num2.asSInt
  alu1_2.io.op := io.input.op1_2

  if (Debug) switch(io.input.special) {
    is(trap) {
      wireExit := ExitReasons.trap
    }
    is(inv) {
      wireExit := ExitReasons.inv
    }
  }

  io.lastVR.READY := io.nextVR.READY

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    data    := wireData
    wcsr    := io.input.wcsr
    when(io.input.op1_3 === 0.U) { csrData := io.input.num2 }
    .elsewhen(io.input.op1_3 === 1.U) { csrData := io.input.num2 | io.input.num1 }
    .elsewhen(io.input.op1_3 === 2.U) { csrData := io.input.num2 & (~io.input.num1) }
    csrData := io.input.num2
    isMem   := wireIsMem
    isLd    := wireIsLd
    addr    := wireAddr
    mask    := wireMask
    if (Debug) {
      exit   := wireExit
      pc     := io.input.debug.pc
    }
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    NVALID := 0.B
  }

  if (debugIO) {
    printf("ex_last_ready    = %d\n", io.lastVR.READY )
    printf("ex_last_valid    = %d\n", io.lastVR.VALID )
    printf("ex_next_ready    = %d\n", io.nextVR.READY )
    printf("ex_next_valid    = %d\n", io.nextVR.VALID )
    printf("io.output.rd     = %d\n", io.output.rd    )
    printf("io.output.data   = %d\n", io.output.data  )
    printf("io.output.isMem  = %d\n", io.output.isMem )
    printf("io.output.isLd   = %d\n", io.output.isLd  )
    printf("io.output.addr   = %d\n", io.output.addr  )
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
  }
}

package cpu.pipeline

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import utils.Convert._

import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import ExecSpecials._
import cpu.component._
import cpu.tools._

class EX(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val input  = Flipped(new IDOutput)
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val output = new EXOutput
  })

  val alu1_2 = Module(new ALU)
  alu1_2.io.a  := io.input.num(0).asSInt
  alu1_2.io.b  := io.input.num(1).asSInt
  alu1_2.io.op := io.input.op1_2

  val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID

  val rd      = RegInit(0.U(5.W))
  val data    = RegInit(0.U(xlen.W))
  val wcsr    = RegInit(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W))))
  val csrData = RegInit(VecInit(Seq.fill(writeCsrsPort)(0.U(xlen.W))))
  val isMem   = RegInit(0.B)
  val isLd    = RegInit(0.B)
  val addr    = RegInit(0.U(xlen.W))
  val mask    = RegInit(0.U(3.W))
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val pc      = if (Debug) RegInit(0.U(xlen.W)) else null

  val wireRd      = WireDefault(UInt(5.W), io.input.rd)
  val wireData    = WireDefault(UInt(xlen.W), alu1_2.io.res.asUInt)
  val wireCsrData = WireDefault(VecInit(Seq.fill(writeCsrsPort)(0.U(xlen.W))))
  val wireIsMem   = WireDefault(Bool(), io.input.special === ld || io.input.special === st)
  val wireIsLd    = WireDefault(Bool(), io.input.special === ld)
  val wireAddr    = WireDefault(UInt(xlen.W), io.input.num(2) + io.input.num(3))
  val wireMask    = WireDefault(UInt(3.W), io.input.op1_3)
  val wireExit    = if (Debug) WireDefault(UInt(3.W), ExitReasons.non) else null

  io.output.rd      := rd
  io.output.data    := data
  io.output.wcsr    := wcsr
  io.output.csrData := csrData
  io.output.isMem   := isMem
  io.output.isLd    := isLd
  io.output.addr    := addr
  io.output.mask    := mask

  when(io.input.special === word) {
    wireData := Fill(32, alu1_2.io.res(31)) ## alu1_2.io.res(31, 0)
  }

  when(io.input.special === csr) {
    switch(io.input.op1_3) {
      is(0.U) { wireCsrData(0) := io.input.num(1) }
      is(1.U) { wireCsrData(0) := io.input.num(0) | io.input.num(1) }
      is(2.U) { wireCsrData(0) := io.input.num(0) & (~io.input.num(1)) }
    }
  }
  when(io.input.special === inv) {
    wireCsrData(0) := io.input.num(0)
    wireCsrData(1) := 2.U
    wireCsrData(2) := io.input.num(2)
    wireCsrData(3) := Cat(
      io.input.num(3)(xlen - 1, 13),
      "b11".U,
      io.input.num(3)(10, 8),
      io.input.num(3)(3), // MIE
      io.input.num(3)(6, 4),
      0.B,
      io.input.num(3)(2, 0)
    )
    if (Debug) printf("Invalid Instruction!\n")
  }
  when(io.input.special === mret) {
    wireCsrData(0) := Cat(
      io.input.num(0)(xlen - 1, 8),
      1.B,
      io.input.num(0)(6, 4),
      io.input.num(0)(7), // MPIE
      io.input.num(0)(2, 0)
    )
  }
  when(io.input.special === int) {
    wireCsrData(0) := io.input.num(0)
    wireCsrData(1) := io.input.num(1)
    wireCsrData(2) := io.input.num(2)
    wireCsrData(3) := Cat(
      io.input.num(3)(xlen - 1, 13),
      "b11".U,
      io.input.num(3)(10, 8),
      io.input.num(3)(3), // MIE
      io.input.num(3)(6, 4),
      0.B,
      io.input.num(3)(2, 0)
    )
    wireRd := 0.U
  }

  if (Debug) switch(io.input.special) {
    is(trap) { wireExit := ExitReasons.trap }
    is(inv)  { wireExit := ExitReasons.inv  }
  }

  io.lastVR.READY := io.nextVR.READY

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    data    := wireData
    wcsr    := io.input.wcsr
    csrData := wireCsrData
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

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
  }
}

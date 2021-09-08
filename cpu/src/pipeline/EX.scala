package cpu.pipeline

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import utils.Convert._

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

  val alu        = Module(new ALU)
  val op         = RegInit(0.U(AluTypeWidth.W))
  val wireOp     = WireDefault(UInt(AluTypeWidth.W), op)
  val isWord     = RegInit(0.B)
  val wireIsWord = WireDefault(Bool(), isWord)
  alu.io.input.bits.a    := io.input.num(0).asSInt
  alu.io.input.bits.b    := io.input.num(1).asSInt
  alu.io.input.bits.op   := wireOp
  alu.io.input.bits.word := wireIsWord
  alu.io.input.bits.sign := ((io.input.special =/= mu) && (io.input.special =/= msu)) ## (io.input.special =/= mu)
  alu.io.input.valid     := io.lastVR.VALID
  alu.io.output.ready    := io.nextVR.READY

  val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID

  val rd      = RegInit(0.U(5.W))
  val data    = RegInit(0.U(xlen.W))
  val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  val csrData = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))))
  val isMem   = RegInit(0.B)
  val isLd    = RegInit(0.B)
  val addr    = RegInit(0.U(alen.W))
  val mask    = RegInit(0.U(3.W))
  val exit    = if (Debug) RegInit(0.U(3.W)) else null
  val pc      = if (Debug) RegInit(0.U(alen.W)) else null

  val wireRd      = WireDefault(UInt(5.W), io.input.rd)
  val wireData    = WireDefault(UInt(xlen.W), alu.io.output.bits.asUInt)
  val wireCsrData = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))))
  val wireIsMem   = WireDefault(Bool(), io.input.special === ld || io.input.special === st)
  val wireIsLd    = WireDefault(Bool(), io.input.special === ld)
  val wireAddr    = WireDefault(UInt(alen.W), io.input.num(2)(alen - 1, 0) + io.input.num(3)(alen - 1, 0))
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
      io.input.num(0)(xlen - 1, 13),
      0.U(2.W),
      io.input.num(0)(10, 8),
      1.B,
      io.input.num(0)(6, 4),
      io.input.num(0)(7), // MPIE
      io.input.num(0)(2, 0)
    )
  }
  when(io.input.special === exception) {
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

  io.lastVR.READY := io.nextVR.READY && alu.io.input.ready

  import cpu.component.Operators.{mul, ruw}
  when(alu.io.output.fire && ((op >= mul) && (op <= ruw))) {
    io.output.data  := alu.io.output.bits.asUInt
    data            := alu.io.output.bits.asUInt
    io.nextVR.VALID := 1.B
    NVALID          := 1.B
    op              := 0.U
  }

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID := (io.input.op1_2 < mul) || (io.input.op1_2 > ruw)
    rd         := wireRd
    data       := wireData
    wcsr       := io.input.wcsr
    csrData    := wireCsrData
    isMem      := wireIsMem
    isLd       := wireIsLd
    addr       := wireAddr
    mask       := wireMask
    op         := wireOp
    isWord     := wireIsWord
    wireOp     := io.input.op1_2
    wireIsWord := (io.input.special === word)
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

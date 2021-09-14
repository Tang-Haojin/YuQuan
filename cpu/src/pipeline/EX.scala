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
    val invIch = Irrevocable(UInt(0.W))
    val wbDch  = Irrevocable(UInt(0.W))
  })

  io.invIch.bits := DontCare; io.wbDch.bits := DontCare

  private val idle::storing::Nil = Enum(2)
  private val alu        = Module(new ALU)
  private val op         = RegInit(0.U(AluTypeWidth.W))
  private val wireOp     = WireDefault(UInt(AluTypeWidth.W), op)
  private val isWord     = RegInit(0.B)
  private val wireIsWord = WireDefault(Bool(), isWord)
  alu.io.input.bits.a    := io.input.num(0).asSInt
  alu.io.input.bits.b    := io.input.num(1).asSInt
  alu.io.input.bits.op   := wireOp
  alu.io.input.bits.word := wireIsWord
  alu.io.input.bits.sign := ((io.input.special =/= mu) && (io.input.special =/= msu)) ## (io.input.special =/= mu)
  alu.io.input.valid     := io.lastVR.VALID
  alu.io.output.ready    := io.nextVR.READY

  private val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID

  private val invalidateICache = RegInit(0.B)
  private val writebackDCache  = RegInit(0.B)

  private val rd      = RegInit(0.U(5.W))
  private val data    = RegInit(0.U(xlen.W))
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val csrData = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))))
  private val isMem   = RegInit(0.B)
  private val isLd    = RegInit(0.B)
  private val addr    = RegInit(0.U(alen.W))
  private val mask    = RegInit(0.U(3.W))
  private val retire  = RegInit(0.B)
  private val lraddr  = RegInit(0.U(alen.W))
  private val lrvalid = RegInit(0.B)
  private val scState = RegInit(UInt(1.W), idle)
  private val tmpRd   = RegInit(0.U(5.W))
  private val exit    = if (Debug) RegInit(0.U(3.W)) else null
  private val pc      = if (Debug) RegInit(0.U(alen.W)) else null
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null

  private val wireRd      = WireDefault(UInt(5.W), io.input.rd)
  private val wireData    = WireDefault(UInt(xlen.W), alu.io.output.bits.asUInt)
  private val wireCsrData = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))))
  private val wireIsMem   = WireDefault(Bool(), io.input.special === ld || io.input.special === st)
  private val wireIsLd    = WireDefault(Bool(), io.input.special === ld)
  private val wireAddr    = WireDefault(UInt(alen.W), io.input.num(2)(alen - 1, 0) + io.input.num(3)(alen - 1, 0))
  private val wireMask    = WireDefault(UInt(3.W), io.input.op1_3)
  private val wireRetire  = WireDefault(Bool(), io.input.retire)
  private val wireLraddr  = WireDefault(UInt(alen.W), lraddr)
  private val wireLrvalid = WireDefault(Bool(), lrvalid)
  private val wireScState = WireDefault(UInt(1.W), scState)
  private val wireTmpRd   = WireDefault(UInt(5.W), tmpRd)
  private val wireExit    = if (Debug) WireDefault(UInt(3.W), ExitReasons.non) else null

  io.output.rd      := rd
  io.output.data    := data
  io.output.wcsr    := wcsr
  io.output.csrData := csrData
  io.output.isMem   := isMem
  io.output.isLd    := isLd
  io.output.addr    := addr
  io.output.mask    := mask
  io.output.retire  := retire

  io.invIch.valid   := invalidateICache
  io.wbDch.valid    := writebackDCache
  when(io.invIch.fire) { invalidateICache := 0.B }
  when(io.wbDch.fire)  { writebackDCache  := 0.B }

  when(io.input.special === csr) {
    wireCsrData(0) := MuxLookup(io.input.op1_3, 0.U, Seq(
      0.U -> (io.input.num(1)),
      1.U -> (io.input.num(0) | io.input.num(1)),
      2.U -> (io.input.num(0) & ~io.input.num(1))
    ))
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
  if (extensions.contains('S')) when(io.input.special === sret) {
    wireCsrData(0) := Cat(
      io.input.num(0)(xlen - 1, 9),
      1.B,
      io.input.num(0)(7, 3),
      io.input.num(0)(5), // SPIE
      io.input.num(0)(1, 0)
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
  if (extensions.contains('A')) when(io.input.special === amo) {
    when(io.input.op1_2 === Operators.lr) {
      wireIsMem   := 1.B
      wireIsLd    := 1.B
      wireLraddr  := wireAddr
      wireLrvalid := 1.B
    }
    when(io.input.op1_2 === Operators.sc) {
      when(wireAddr =/= lraddr || !lrvalid) { wireData := 1.U }
      .otherwise {
        wireScState := storing
        wireLrvalid := 0.B
        wireRetire  := 0.B
        wireRd      := 0.U
        wireIsMem   := 1.B
        wireTmpRd   := io.input.rd
      }
    }
  }

  if (Debug) switch(io.input.special) {
    is(trap) { wireExit := ExitReasons.trap }
    is(inv)  { wireExit := ExitReasons.inv  }
  }

  io.lastVR.READY := io.nextVR.READY && alu.io.input.ready && !invalidateICache && !writebackDCache && scState === idle

  import Operators.{mul, ruw}
  when(alu.io.output.fire && ((op >= mul) && (op <= ruw))) {
    io.output.data  := alu.io.output.bits.asUInt
    data            := alu.io.output.bits.asUInt
    io.nextVR.VALID := 1.B
    NVALID          := 1.B
    op              := 0.U
  }

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := (io.input.op1_2 < mul) || (io.input.op1_2 > ruw)
    rd      := wireRd
    data    := wireData
    wcsr    := io.input.wcsr
    csrData := wireCsrData
    isMem   := wireIsMem
    isLd    := wireIsLd
    addr    := wireAddr
    mask    := wireMask
    retire  := wireRetire
    lraddr  := wireLraddr
    lrvalid := wireLrvalid
    scState := wireScState
    tmpRd   := wireTmpRd

    op      := wireOp
    isWord  := wireIsWord

    invalidateICache := io.input.special === fencei
    writebackDCache  := io.input.special === fencei

    wireOp     := io.input.op1_2
    wireIsWord := (io.input.special === word)
    if (Debug) {
      exit := wireExit
      pc   := io.input.debug.pc
      rcsr := io.input.debug.rcsr
    }
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    NVALID := 0.B
  }

  if (extensions.contains('A')) when(scState === storing) {
    scState := idle
    rd      := tmpRd
    data    := 0.U
    isMem   := 0.B
    retire  := 1.B
    NVALID  := 1.B
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
    io.output.debug.rcsr := rcsr
  }
}

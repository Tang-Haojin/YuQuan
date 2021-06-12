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
  val rd    = Output(UInt(5.W))
  val data  = Output(UInt(XLEN.W))
  val isMem = Output(Bool())
  val isLd  = Output(Bool())
  val addr  = Output(UInt(XLEN.W))
  val mask  = Output(UInt(3.W))
  val exit  = Output(UInt(3.W))
}

class EX extends Module {
  val io = IO(new Bundle {
    val pcIo   = Flipped(new PCIO)
    val input  = Flipped(new IDOutput)
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val output = new EXOutput
  })

  io.pcIo.wen   := 0.B
  io.pcIo.wdata := 0.U

  val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID
  val LREADY = RegInit(1.B); io.lastVR.READY := LREADY

  val rd     = RegInit(0.U(5.W))
  val data   = RegInit(0.U(XLEN.W))
  val isMem  = RegInit(0.B)
  val isLd   = RegInit(0.B)
  val addr   = RegInit(0.U(XLEN.W))
  val mask   = RegInit(0.U(3.W))
  val exit   = RegInit(0.U(3.W))

  val wireRd    = Wire(UInt(5.W))
  val wireData  = Wire(UInt(XLEN.W))
  val wireSpec  = Wire(UInt(XLEN.W))
  val wireIsMem = Wire(Bool())
  val wireIsLd  = Wire(Bool())
  val wireAddr  = Wire(UInt(XLEN.W));
  val wireMask  = Wire(UInt(3.W))
  val wireExit  = Wire(UInt(3.W))

  wireRd    := io.input.rd
  wireIsMem := (io.input.special === ld || io.input.special === st)
  wireIsLd  := (io.input.special === ld)
  wireAddr  := io.input.num3 + io.input.num4
  wireMask  := io.input.op1_3
  wireExit  := ExitReasons.non

  io.output.rd    := rd
  io.output.data  := data
  io.output.isMem := isMem
  io.output.isLd  := isLd
  io.output.addr  := addr
  io.output.mask  := mask
  io.output.exit  := exit

  val alu1_2 = Module(new ALU)
  val alu1_3 = Module(new ALU)

  wireData  := alu1_2.io.res.asUInt
  when(io.input.special === word) {
    wireData := Cat(Fill(32, alu1_2.io.res(31)), alu1_2.io.res(31, 0))
  }
  alu1_2.io.a  := io.input.num1.asSInt
  alu1_2.io.b  := io.input.num2.asSInt
  alu1_2.io.op := io.input.op1_2

  wireSpec     := alu1_3.io.res.asUInt
  alu1_3.io.a  := io.input.num1.asSInt
  alu1_3.io.b  := io.input.num3.asSInt
  alu1_3.io.op := io.input.op1_3

  switch(io.input.special) {
    is(trap) {
      wireExit := ExitReasons.trap
    }
    is(inv) {
      wireExit := ExitReasons.inv
    }
  }

  // FSM
  when(io.nextVR.VALID && io.nextVR.READY) { // ready to trans result to the next level
    NVALID := 0.B
    LREADY := 1.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID := 1.B
    LREADY := 0.B
    rd     := wireRd
    data   := wireData
    isMem  := wireIsMem
    isLd   := wireIsLd
    addr   := wireAddr
    mask   := wireMask
    exit   := wireExit

    io.pcIo.wen   := 1.B
    io.pcIo.wdata := io.pcIo.rdata + 4.U

    switch(io.input.special) {
      is(jump) {
        io.pcIo.wdata := wireSpec
      }
      is(jalr) {
        io.pcIo.wdata := Cat((io.input.num3 + io.input.num4)(XLEN - 1, 1), 0.U)
      }
      is(branch) {
        when(wireData === 1.U) {
          io.pcIo.wdata := io.pcIo.rdata + io.input.num3
        }
      }
    }
  }

  if (debugIO && false) {
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
}

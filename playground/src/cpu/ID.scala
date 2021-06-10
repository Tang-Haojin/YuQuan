package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import cpu.ExecSpecials._
import cpu.InstrTypes._


object ExecSpecials {
  val specials = Enum(9)
  val non::ld::st::jump::jalr::branch::trap::inv::word::Nil = specials
}

object InstrTypes {
  val instrtypes = Enum(7)
  val i::u::s::r::j::b::t::Nil = instrtypes
}

object NumTypes {
  val numtypes = Enum(7)
  val non::rs1::rs2::imm::four::pc::fun3::Nil = numtypes
}

class IDOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val num1    = Output(UInt(XLEN.W))
  val num2    = Output(UInt(XLEN.W))
  val num3    = Output(UInt(XLEN.W))
  val num4    = Output(UInt(XLEN.W))
  val op1_2   = Output(UInt(AluTypeWidth.W))
  val op1_3   = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(5.W))
}

// instruction decoding module
class ID extends Module {
  val io = IO(new Bundle {
    val pcIo   = Flipped(new PCIO)    // connected
    val output = new IDOutput         // connected
    val gprsR  = Flipped(new GPRsR)   // connected
    val lastVR = new LastVR           // connected
    val nextVR = Flipped(new LastVR)  // connected
    val instr  = Input (UInt(32.W))   // connected
  })

  io.pcIo.wen   := 0.B
  io.pcIo.wdata := 0.U

  val NVALID  = RegInit(0.B)
  val LREADY  = RegInit(1.B)
  val rd      = RegInit(0.U(5.W))
  val op1_2   = RegInit(0.U(AluTypeWidth.W))
  val op1_3   = RegInit(0.U(AluTypeWidth.W))
  val special = RegInit(0.U(5.W))

  val (num1, num2, num3, num4) = (
    RegInit(0.U(XLEN.W)), RegInit(0.U(XLEN.W)),
    RegInit(0.U(XLEN.W)), RegInit(0.U(XLEN.W))
  )
  
  val decoded = ListLookup(
    io.instr,
    List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv),
    RVI.table
  )

  val wireSpecial = WireDefault(0.U(5.W))
  val wireType    = WireDefault(7.U(3.W))
  val wireRd      = Wire(UInt(5.W))
  val wireOp1_2   = Wire(UInt(AluTypeWidth.W)); wireOp1_2 := decoded(5)
  val wireOp1_3   = Wire(UInt(AluTypeWidth.W)); wireOp1_3 := decoded(6)
  val wireFunt3   = Wire(UInt(3.W));            wireFunt3 := io.instr(14, 12)

  val (wireNum1, wireNum2, wireNum3, wireNum4, wireImm) = (
    WireDefault(0.U(XLEN.W)), WireDefault(0.U(XLEN.W)),
    WireDefault(0.U(XLEN.W)), WireDefault(0.U(XLEN.W)),
    WireDefault(0.U(XLEN.W))
  )

  val (wireRs1, wireRs2) = (
    Wire(UInt(5.W)), Wire(UInt(5.W))
  ); wireRs1 := io.instr(19, 15); wireRs2 := io.instr(24, 20)

  val (wireDataRs1, wireDataRs2) = (
    Wire(UInt(XLEN.W)), Wire(UInt(XLEN.W))
  ); wireDataRs1 := io.gprsR.rdata(0); wireDataRs2 := io.gprsR.rdata(1)

  io.nextVR.VALID   := NVALID
  io.lastVR.READY   := LREADY
  io.output.rd      := rd
  io.output.num1    := num1
  io.output.num2    := num2
  io.output.num3    := num3
  io.output.num4    := num4
  io.output.op1_2   := op1_2
  io.output.op1_3   := op1_3
  io.output.special := special
  io.gprsR.raddr(0) := wireRs1
  io.gprsR.raddr(1) := wireRs2
  io.gprsR.raddr(2) := 10.U

  val numList = List(
    (wireNum1, decoded(1)), (wireNum2, decoded(2)),
    (wireNum3, decoded(3)), (wireNum4, decoded(4))
  )
  
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
      is(NumTypes.four) {
        num._1 := 4.U
      }
      is(NumTypes.pc) {
        num._1 := io.pcIo.rdata
      }
      is(NumTypes.non) {
        num._1 := 0.U
      }
      is(NumTypes.fun3) {
        num._1 := wireFunt3
      }
    }
  }

  io.output.special := decoded(8)

  switch(decoded(0)) {
    is(i) {
      wireImm := Cat(Fill(XLEN - 12, io.instr(31)), io.instr(31, 20))
    }
    is(u) {
      wireImm := Cat(Fill(XLEN - 32, io.instr(31)), io.instr(31, 12), Fill(12, 0.U))
    }
    is(j) {
      wireImm := Cat(
        Fill(XLEN - 20, io.instr(31)),
        io.instr(19, 12),
        io.instr(20),
        io.instr(30, 21),
        0.U
      )
    }
    is(s) {
      wireImm := Cat(
        Fill(XLEN - 12, io.instr(31)),
        io.instr(31, 25),
        io.instr(11, 7 )
      )
    }
    is(b) {
      wireImm := Cat(
        Fill(XLEN - 12, io.instr(31)),
        io.instr(7),
        io.instr(30, 25),
        io.instr(11, 8 ),
        0.U
      )
    }
    is(t) {
      wireImm := io.gprsR.rdata(2)
    }
  }
  
  when(decoded(7) === 1.U) {
    wireRd := io.instr(11, 7)
  }.otherwise {
    wireRd := 0.U
  }

  // FSM
  when(io.nextVR.VALID && io.nextVR.READY) { // ready to trans instr to the next level
    NVALID  := 0.B
    LREADY  := 1.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    LREADY  := 0.B
    rd      := wireRd
    num1    := wireNum1
    num2    := wireNum2
    num3    := wireNum3
    num4    := wireNum4
    op1_2   := wireOp1_2
    op1_3   := wireOp1_3
    special := wireSpecial
  }

  if (debugIO && false) {
    printf("id_last_ready     = %d\n", io.lastVR.READY  )
    printf("id_last_valid     = %d\n", io.lastVR.VALID  )
    printf("id_next_ready     = %d\n", io.nextVR.READY  )
    printf("id_next_valid     = %d\n", io.nextVR.VALID  )
    printf("io.instr          = %x\n", io.instr         )
    printf("io.output.rd      = %d\n", io.output.rd     )
    printf("io.output.num1    = %d\n", io.output.num1   )
    printf("io.output.num2    = %d\n", io.output.num2   )
    printf("io.output.num3    = %d\n", io.output.num3   )
    printf("io.output.num4    = %d\n", io.output.num4   )
    printf("io.output.op1_2   = %d\n", io.output.op1_2  )
    printf("io.output.op1_3   = %d\n", io.output.op1_3  )
    printf("io.output.special = %d\n", io.output.special)
  }
}

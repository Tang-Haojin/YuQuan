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
import org.apache.commons.lang3.builder.Diff


object ExecSpecials {
  val specials = Enum(10)
  val non::ld::st::jump::jalr::branch::trap::inv::word::csr::Nil = specials
}

object InstrTypes {
  val instrtypes = Enum(7)
  val i::u::s::r::j::b::inscsr::Nil = instrtypes
}

object NumTypes {
  val numtypes = Enum(8)
  val non::rs1::rs2::imm::four::pc::fun3::csr::Nil = numtypes
}

object RVInstr {
  val table = RVI.table ++ (if (HasRVM) RVM.table else Nil)
}

class IDOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val csr     = Output(UInt(12.W))
  val num1    = Output(UInt(XLEN.W))
  val num2    = Output(UInt(XLEN.W))
  val num3    = Output(UInt(XLEN.W))
  val num4    = Output(UInt(XLEN.W))
  val op1_2   = Output(UInt(AluTypeWidth.W))
  val op1_3   = Output(UInt(AluTypeWidth.W))
  val special = Output(UInt(5.W))
  val debug   =
  if (Debug) new Bundle {
    val pc = Output(UInt(XLEN.W))
  } else null
}

// instruction decoding module
class ID extends Module {
  val io = IO(new Bundle {
    val output = new IDOutput
    val gprsR  = Flipped(new GPRsR)
    val lastVR = new LastVR
    val nextVR = Flipped(new LastVR)
    val input  = Flipped(new IFOutput)
    val jmpBch = Output(Bool())
    val jbAddr = Output(UInt(XLEN.W))
    val isWait = Input (Bool())
  })

  val NVALID  = RegInit(0.B)
  val rd      = RegInit(0.U(5.W))
  val op1_2   = RegInit(0.U(AluTypeWidth.W))
  val op1_3   = RegInit(0.U(AluTypeWidth.W))
  val special = RegInit(0.U(5.W))
  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null

  val (num1, num2, num3, num4) = (
    RegInit(0.U(XLEN.W)), RegInit(0.U(XLEN.W)),
    RegInit(0.U(XLEN.W)), RegInit(0.U(XLEN.W))
  )
  
  val decoded = ListLookup(
    io.input.instr,
    List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv),
    RVInstr.table
  )

  val wireSpecial = WireDefault(0.U(5.W));      wireSpecial := decoded(8)
  val wireType    = WireDefault(7.U(3.W))
  val wireRd      = Wire(UInt(5.W))
  val wireOp1_2   = Wire(UInt(AluTypeWidth.W)); wireOp1_2   := decoded(5)
  val wireOp1_3   = Wire(UInt(AluTypeWidth.W)); wireOp1_3   := decoded(6)
  val wireFunt3   = Wire(UInt(3.W));            wireFunt3   := io.input.instr(14, 12)

  val (wireNum1, wireNum2, wireNum3, wireNum4, wireImm) = (
    WireDefault(0.U(XLEN.W)), WireDefault(0.U(XLEN.W)),
    WireDefault(0.U(XLEN.W)), WireDefault(0.U(XLEN.W)),
    WireDefault(0.U(XLEN.W))
  )

  val (wireRs1, wireRs2) = (
    Wire(UInt(5.W)), Wire(UInt(5.W))
  ); wireRs1 := io.input.instr(19, 15); wireRs2 := io.input.instr(24, 20)

  val (wireDataRs1, wireDataRs2) = (
    Wire(UInt(XLEN.W)), Wire(UInt(XLEN.W))
  ); wireDataRs1 := io.gprsR.rdata(0); wireDataRs2 := io.gprsR.rdata(1)

  val wireData    = Wire(UInt(XLEN.W))

  val alu1_2 = Module(new SimpleALU)
  wireData  := alu1_2.io.res.asUInt
  alu1_2.io.a  := wireNum1.asSInt
  alu1_2.io.b  := wireNum2.asSInt
  alu1_2.io.op := wireOp1_2

  io.nextVR.VALID   := NVALID
  io.output.rd      := rd
  io.output.csr     := 0xFFF.U
  io.output.num1    := num1
  io.output.num2    := num2
  io.output.num3    := num3
  io.output.num4    := num4
  io.output.op1_2   := op1_2
  io.output.op1_3   := op1_3
  io.output.special := special
  io.gprsR.raddr(0) := 0.U
  io.gprsR.raddr(1) := 0.U
  io.gprsR.raddr(2) := 10.U

  for (i <- 1 to 4) {
    when(decoded(i) === NumTypes.rs1) {
      io.gprsR.raddr(0) := wireRs1
    }.elsewhen(decoded(i) === NumTypes.rs2) {
      io.gprsR.raddr(1) := wireRs2
    }
  }

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
        num._1 := io.input.pc
      }
      is(NumTypes.non) {
        num._1 := 0.U
      }
      is(NumTypes.fun3) {
        num._1 := wireFunt3
      }
    }
  }

  switch(decoded(0)) {
    is(i) {
      wireImm := Cat(Fill(XLEN - 12, io.input.instr(31)), io.input.instr(31, 20))
    }
    is(u) {
      wireImm := Cat(
        if (XLEN == 64) Fill(XLEN - 32, io.input.instr(31)) else 0.U,
        io.input.instr(31, 12),
        Fill(12, 0.U)
      )
    }
    is(j) {
      wireImm := Cat(
        Fill(XLEN - 20, io.input.instr(31)),
        io.input.instr(19, 12),
        io.input.instr(20),
        io.input.instr(30, 21),
        0.U
      )
    }
    is(s) {
      wireImm := Cat(
        Fill(XLEN - 12, io.input.instr(31)),
        io.input.instr(31, 25),
        io.input.instr(11, 7 )
      )
    }
    is(b) {
      wireImm := Cat(
        Fill(XLEN - 12, io.input.instr(31)),
        io.input.instr(7),
        io.input.instr(30, 25),
        io.input.instr(11, 8 ),
        0.U
      )
    }
    is(inscsr) {
      wireImm := Cat(Fill(XLEN - 5, 0.U), io.input.instr(19, 15))
    }
  }
  
  when(decoded(7) === 1.U) {
    wireRd := io.input.instr(11, 7)
  }.otherwise {
    wireRd := 0.U
  }

  io.jmpBch := 0.B
  io.jbAddr := 0.U
  switch(wireSpecial) {
    is(jump) {
      io.jmpBch := 1.B
      io.jbAddr := io.input.pc + wireImm
    }
    is(jalr) {
      io.jmpBch := 1.B
      io.jbAddr := Cat((wireImm + wireDataRs1)(XLEN - 1, 1), 0.U)
    }
    is(branch) {
      when(wireData === 1.U) {
        io.jmpBch := 1.B
        io.jbAddr := io.input.pc + wireImm
      }
    }
    is(csr) {
      
    }
  }

  io.lastVR.READY := io.nextVR.READY && !io.isWait
  
  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    num1    := wireNum1
    num2    := wireNum2
    num3    := wireNum3
    num4    := wireNum4
    op1_2   := wireOp1_2
    op1_3   := wireOp1_3
    special := wireSpecial
    if (Debug) pc := io.input.pc
  }.elsewhen(io.isWait && io.nextVR.READY) {
    NVALID  := 0.B
    rd      := 0.U
    num1    := 0.U
    num2    := 0.U
    num3    := 0.U
    num4    := 0.U
    op1_2   := 0.U
    op1_3   := 0.U
    special := 0.U
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    NVALID := 0.B
  }

  if (debugIO) {
    printf("id_last_ready     = %d\n", io.lastVR.READY  )
    printf("id_last_valid     = %d\n", io.lastVR.VALID  )
    printf("id_next_ready     = %d\n", io.nextVR.READY  )
    printf("id_next_valid     = %d\n", io.nextVR.VALID  )
    printf("io.input.instr    = %x\n", io.input.instr   )
    printf("io.input.pc       = %x\n", io.input.pc      )
    printf("io.output.rd      = %d\n", io.output.rd     )
    printf("io.output.num1    = %x\n", io.output.num1   )
    printf("io.output.num2    = %x\n", io.output.num2   )
    printf("io.output.num3    = %x\n", io.output.num3   )
    printf("io.output.num4    = %x\n", io.output.num4   )
    printf("io.output.op1_2   = %d\n", io.output.op1_2  )
    printf("io.output.op1_3   = %d\n", io.output.op1_3  )
    printf("io.output.special = %d\n", io.output.special)
  }

  if (Debug) {
    io.output.debug.pc := pc
  }
}

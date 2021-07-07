package cpu

import chisel3._
import chisel3.util._

import tools._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import cpu.ExecSpecials._
import cpu.InstrTypes._


object ExecSpecials {
  val specials = Enum(12)
  val non::ld::st::jump::jalr::branch::trap::inv::word::csr::mret::int::Nil = specials
}

object InstrTypes { val i::u::s::r::j::b::c::Nil = Enum(7) }

object NumTypes {
  val numtypes = Enum(8)
  val non::rs1::rs2::imm::four::pc::fun3::csr::Nil = numtypes
}

object RVInstr {
  val table = RVI.table ++ cpu.privileged.Zicsr.table ++ (if (HasRVM) RVM.table else Nil)
}

class IDOutput extends Bundle {
  val rd      = Output(UInt(5.W))
  val wcsr    = Output(Vec(writeCsrsPort, UInt(12.W)))
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
    val output    = new IDOutput
    val gprsR     = Flipped(new GPRsR)
    val csrsR     = Flipped(new cpu.privileged.CSRsR)
    val lastVR    = new LastVR
    val nextVR    = Flipped(new LastVR)
    val input     = Flipped(new IFOutput)
    val jmpBch    = Output(Bool())
    val jbAddr    = Output(UInt(XLEN.W))
    val isWait    = Input (Bool())
  })

  val csrsRdata = io.csrsR.rdata
  class CSRsAddr extends cpu.privileged.CSRsAddr
  val csrsAddr = new CSRsAddr

  val NVALID  = RegInit(0.B)
  val rd      = RegInit(0.U(5.W))
  val wcsr    = RegInit(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W))))
  val op1_2   = RegInit(0.U(AluTypeWidth.W))
  val op1_3   = RegInit(0.U(AluTypeWidth.W))
  val special = RegInit(0.U(5.W))
  val instr   = RegInit(0.U(32.W))
  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null

  val num1 = RegInit(0.U(XLEN.W))
  val num2 = RegInit(0.U(XLEN.W))
  val num3 = RegInit(0.U(XLEN.W))
  val num4 = RegInit(0.U(XLEN.W))
  
  val decoded = ListLookup(
    io.input.instr,
    List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv),
    RVInstr.table
  )

  val wireSpecial = WireDefault(UInt(5.W), decoded(8))
  val wireType    = WireDefault(7.U(3.W))
  val wireRd      = Wire(UInt(5.W))
  val wireCsr     = WireDefault(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W))))
  val wireOp1_2   = WireDefault(UInt(AluTypeWidth.W), decoded(5))
  val wireOp1_3   = WireDefault(UInt(AluTypeWidth.W), decoded(6))
  val wireFunt3   = WireDefault(UInt(3.W), io.input.instr(14, 12))
  val wireNum1    = WireDefault(0.U(XLEN.W))
  val wireNum2    = WireDefault(0.U(XLEN.W))
  val wireNum3    = WireDefault(0.U(XLEN.W))
  val wireNum4    = WireDefault(0.U(XLEN.W))
  val wireImm     = WireDefault(0.U(XLEN.W))
  val wireRs1     = WireDefault(UInt(5.W), io.input.instr(19, 15))
  val wireRs2     = WireDefault(UInt(5.W), io.input.instr(24, 20))
  val wireDataRs1 = WireDefault(UInt(XLEN.W), io.gprsR.rdata(0))
  val wireDataRs2 = WireDefault(UInt(XLEN.W), io.gprsR.rdata(1))

  val wireData    = Wire(UInt(XLEN.W))

  val alu1_2 = Module(new SimpleALU)
  wireData  := alu1_2.io.res.asUInt
  alu1_2.io.a  := wireNum1.asSInt
  alu1_2.io.b  := wireNum2.asSInt
  alu1_2.io.op := wireOp1_2

  io.nextVR.VALID   := NVALID
  io.output.rd      := rd
  io.output.wcsr    := wcsr
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
  io.csrsR.rcsr     := VecInit(Seq.fill(readCsrsPort)(0xFFF.U(12.W)))

  io.csrsR.rcsr(1) := csrsAddr.Mstatus
  io.csrsR.rcsr(2) := csrsAddr.Mie
  io.csrsR.rcsr(7) := csrsAddr.Mip

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
      is(NumTypes.rs1 ) { num._1 := wireDataRs1 }
      is(NumTypes.rs2 ) { num._1 := wireDataRs2 }
      is(NumTypes.imm ) { num._1 := wireImm }
      is(NumTypes.four) { num._1 := 4.U }
      is(NumTypes.pc  ) { num._1 := io.input.pc }
      is(NumTypes.non ) { num._1 := 0.U }
      is(NumTypes.fun3) { num._1 := wireFunt3 }
      is(NumTypes.csr ) { num._1 := io.csrsR.rdata(0) }
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
    is(c) {
      wireImm := Cat(Fill(XLEN - 5, 0.U), io.input.instr(19, 15))
    }
  }
  
  when(decoded(7) === 1.U) {
    wireRd := io.input.instr(11, 7)
  }.otherwise {
    wireRd := 0.U
  }

  val isClint = Module(new IsCLINT)
  isClint.io.addr_in := wireDataRs1 + wireImm

  io.jmpBch := 0.B
  io.jbAddr := 0.U
  switch(decoded(8)) {
    is(ld) {
      when(isClint.io.addr_out =/= 0xFFF.U) {
        io.csrsR.rcsr(0) := isClint.io.addr_out
        switch(decoded(6)) {
          is(0.U) { wireNum1 := Cat(Fill(XLEN - 8 , csrsRdata(0)( 7)), csrsRdata(0)( 7, 0)) }
          is(1.U) { wireNum1 := Cat(Fill(XLEN - 16, csrsRdata(0)(15)), csrsRdata(0)(15, 0)) }
          is(2.U) { wireNum1 := Cat(Fill(XLEN - 32, csrsRdata(0)(31)), csrsRdata(0)(31, 0)) }
          is(3.U) { wireNum1 :=                                        csrsRdata(0)         }
          is(4.U) { wireNum1 := Cat(Fill(XLEN - 8 ,              0.B), csrsRdata(0)( 7, 0)) }
          is(5.U) { wireNum1 := Cat(Fill(XLEN - 16,              0.B), csrsRdata(0)(15, 0)) }
          is(6.U) { wireNum1 := Cat(Fill(XLEN - 32,              0.B), csrsRdata(0)(31, 0)) }
        }
        wireNum2  := non; wireNum3  := non; wireNum4    := non
        wireOp1_2 := non; wireOp1_3 := non; wireSpecial := non
      }
    }
    is(st) {
      when(isClint.io.addr_out =/= 0xFFF.U) {
        io.csrsR.rcsr(0) := isClint.io.addr_out
        wireCsr(0) := isClint.io.addr_out
        switch(decoded(6)) {
          is(0.U) { wireNum2 := Cat(csrsRdata(0)(XLEN - 1,  8), wireDataRs2( 7, 0)) }
          is(1.U) { wireNum2 := Cat(csrsRdata(0)(XLEN - 1, 16), wireDataRs2(15, 0)) }
          is(2.U) { wireNum2 := Cat(csrsRdata(0)(XLEN - 1, 32), wireDataRs2(31, 0)) }
          is(3.U) { wireNum2 :=                                 wireDataRs2         }
        }
        wireNum1  := non; wireNum3  := non; wireNum4    := non
        wireOp1_2 := non; wireOp1_3 := 0.U; wireSpecial := csr
      }
    }
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
      io.csrsR.rcsr(0) := wireCsr(0)

      wireCsr(0)       := io.input.instr(31, 20)
    }
    is(inv) {
      io.csrsR.rcsr(0) := csrsAddr.Mtvec
      io.csrsR.rcsr(1) := csrsAddr.Mstatus

      wireCsr(0) := csrsAddr.Mepc
      wireCsr(1) := csrsAddr.Mcause
      wireCsr(2) := csrsAddr.Mtval
      wireCsr(3) := csrsAddr.Mstatus

      wireNum3 := io.input.instr
      wireNum4 := io.csrsR.rdata(1)

      io.jmpBch        := 1.B
      io.jbAddr        := Cat(io.csrsR.rdata(0)(XLEN - 1, 2), 0.U(2.W))
    }
    is(mret) {
      io.csrsR.rcsr(0) := csrsAddr.Mepc
      io.csrsR.rcsr(1) := csrsAddr.Mstatus

      wireCsr(0) := csrsAddr.Mstatus

      wireNum1 := io.csrsR.rdata(1)

      io.jmpBch      := 1.B
      io.jbAddr      := Cat(io.csrsR.rdata(0)(XLEN - 1, 2), 0.U(2.W))
    }
  }

  val handelExtInt = io.csrsR.rdata(1)(3) && io.csrsR.rdata(2)(11) && io.csrsR.rdata(7)(11)
  when(io.lastVR.VALID) {
    when(handelExtInt) {
      wireSpecial := int
      wireRd := 0.U
      io.csrsR.rcsr(5) := csrsAddr.Mtvec

      wireCsr(0) := csrsAddr.Mepc
      wireCsr(1) := csrsAddr.Mcause
      wireCsr(2) := csrsAddr.Mtval
      wireCsr(3) := csrsAddr.Mstatus

      wireNum1 := io.input.pc
      wireNum2 := Cat(1.B, 11.U((XLEN - 1).W))
      wireNum3 := io.input.instr
      wireNum4 := io.csrsR.rdata(1)

      io.jmpBch := 1.B
      when(io.csrsR.rdata(5)(0)) { io.jbAddr := Cat(io.csrsR.rdata(5)(XLEN - 1, 2), 0.U(2.W)) + (11 * 4).U }
      .otherwise { io.jbAddr := Cat(io.csrsR.rdata(5)(XLEN - 1, 2), 0.U(2.W)) }
    }
    when((~handelExtInt) && io.csrsR.rdata(1)(3) && io.csrsR.rdata(2)(7)) { // Machine timer interrupt
      io.csrsR.rcsr(3) := csrsAddr.Mtime
      io.csrsR.rcsr(4) := csrsAddr.Mtimecmp
      when(io.csrsR.rdata(3) >= io.csrsR.rdata(4)) {
        wireSpecial := int
        wireRd := 0.U
        io.csrsR.rcsr(5) := csrsAddr.Mtvec

        wireCsr(0) := csrsAddr.Mepc
        wireCsr(1) := csrsAddr.Mcause
        wireCsr(2) := csrsAddr.Mtval
        wireCsr(3) := csrsAddr.Mstatus

        wireNum1 := io.input.pc
        wireNum2 := Cat(1.B, 7.U((XLEN - 1).W))
        wireNum3 := io.input.instr
        wireNum4 := io.csrsR.rdata(1)

        io.jmpBch := 1.B
        when(io.csrsR.rdata(5)(0)) { io.jbAddr := Cat(io.csrsR.rdata(5)(XLEN - 1, 2), 0.U(2.W)) + (7 * 4).U }
        .otherwise { io.jbAddr := Cat(io.csrsR.rdata(5)(XLEN - 1, 2), 0.U(2.W)) }
      }
    }
  }

  io.lastVR.READY := io.nextVR.READY && !io.isWait

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    wcsr    := wireCsr
    num1    := wireNum1
    num2    := wireNum2
    num3    := wireNum3
    num4    := wireNum4
    op1_2   := wireOp1_2
    op1_3   := wireOp1_3
    special := wireSpecial
    instr   := io.input.instr
    if (Debug) pc := io.input.pc
  }.elsewhen(io.isWait && io.nextVR.READY) {
    NVALID  := 0.B
    rd      := 0.U
    wcsr    := VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W)))
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

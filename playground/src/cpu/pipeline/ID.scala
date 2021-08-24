package cpu.pipeline

import chisel3._
import chisel3.util._

import cpu.component._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import ExecSpecials._
import InstrTypes._
import ExceptionCode._

private object csrsAddr extends cpu.privileged.CSRsAddr

// instruction decoding module
class ID extends Module {
  implicit val io = IO(new IDIO)

  val csrsRdata0 = io.csrsR.rdata(0)

  val NVALID  = RegInit(0.B)
  val rd      = RegInit(0.U(5.W))
  val wcsr    = RegInit(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W))))
  val op1_2   = RegInit(0.U(AluTypeWidth.W))
  val op1_3   = RegInit(0.U(AluTypeWidth.W))
  val special = RegInit(0.U(5.W))
  val instr   = RegInit(0.U(32.W))
  val pc      = if (Debug) RegInit(0.U(XLEN.W)) else null

  val num = RegInit(VecInit(Seq.fill(4)(0.U(XLEN.W))))

  val decoded = ListLookup(
    io.input.instr,
    List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv),
    RVInstr.table
  )

  val wireInstr   = WireDefault(UInt(32.W), io.input.instr)
  val wireSpecial = WireDefault(UInt(5.W), decoded(8))
  val wireType    = WireDefault(7.U(3.W))
  val wireRd      = Wire(UInt(5.W))
  val wireCsr     = WireDefault(VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W))))
  val wireOp1_2   = WireDefault(UInt(AluTypeWidth.W), decoded(5))
  val wireOp1_3   = WireDefault(UInt(AluTypeWidth.W), decoded(6))
  val wireFunt3   = WireDefault(UInt(3.W), wireInstr(14, 12))
  val wireNum     = WireDefault(VecInit(Seq.fill(4)(0.U(XLEN.W))))
  val wireImm     = WireDefault(0.U(XLEN.W))
  val wireRs1     = WireDefault(UInt(5.W), wireInstr(19, 15))
  val wireRs2     = WireDefault(UInt(5.W), wireInstr(24, 20))
  val wireDataRs1 = WireDefault(UInt(XLEN.W), io.gprsR.rdata(0))
  val wireDataRs2 = WireDefault(UInt(XLEN.W), io.gprsR.rdata(1))
  val wireExcept  = WireDefault(VecInit(Seq.fill(16)(0.B)))

  private implicit val implicitParam = (io, wireSpecial, wireRd, wireCsr, wireNum, wireExcept)

  val wireData    = Wire(UInt(XLEN.W))

  val alu1_2 = Module(new SimpleALU)
  wireData  := alu1_2.io.res.asUInt
  alu1_2.io.a  := wireNum(0).asSInt
  alu1_2.io.b  := wireNum(1).asSInt
  alu1_2.io.op := wireOp1_2

  io.nextVR.VALID   := NVALID
  io.output.rd      := rd
  io.output.wcsr    := wcsr
  io.output.num     := num
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

  for (i <- 0 until 4)
    switch(decoded(i + 1)) {
      is(NumTypes.rs1 ) { wireNum(i) := wireDataRs1 }
      is(NumTypes.rs2 ) { wireNum(i) := wireDataRs2 }
      is(NumTypes.imm ) { wireNum(i) := wireImm }
      is(NumTypes.four) { wireNum(i) := 4.U }
      is(NumTypes.pc  ) { wireNum(i) := io.input.pc }
      is(NumTypes.non ) { wireNum(i) := 0.U }
      is(NumTypes.fun3) { wireNum(i) := wireFunt3 }
      is(NumTypes.csr ) { wireNum(i) := io.csrsR.rdata(0) }
    }

  switch(decoded.head) {
    is(i) { wireImm := Fill(XLEN - 12, wireInstr(31)) ## wireInstr(31, 20) }
    is(u) { wireImm := Fill(XLEN - 32, wireInstr(31)) ## wireInstr(31, 12) ## 0.U(12.W) }
    is(j) { wireImm := Cat(Fill(XLEN - 20, wireInstr(31)), wireInstr(19, 12), wireInstr(20), wireInstr(30, 21), 0.B) }
    is(s) { wireImm := Fill(XLEN - 12, wireInstr(31)) ## wireInstr(31, 25) ## wireInstr(11, 7) }
    is(b) { wireImm := Cat(Fill(XLEN - 12, wireInstr(31)), wireInstr(7), wireInstr(30, 25), wireInstr(11, 8), 0.B) }
    is(c) { wireImm := 0.U((XLEN - 5).W) ## wireInstr(19, 15) }
  }

  when(decoded(7) === 1.U) { wireRd := wireInstr(11, 7) }
  .otherwise { wireRd := 0.U }

  val isClint = Module(new IsCLINT)
  isClint.io.addr_in := wireDataRs1 + wireImm

  io.jmpBch := 0.B
  io.jbAddr := 0.U
  switch(decoded(8)) {
    is(ld) {
      when(isClint.io.addr_out =/= 0xFFF.U) {
        io.csrsR.rcsr(0) := isClint.io.addr_out
        switch(decoded(6)) {
          is(0.U) { wireNum(0) := Fill(XLEN - 8 , csrsRdata0( 7)) ## csrsRdata0( 7, 0) }
          is(1.U) { wireNum(0) := Fill(XLEN - 16, csrsRdata0(15)) ## csrsRdata0(15, 0) }
          is(2.U) { wireNum(0) := Fill(XLEN - 32, csrsRdata0(31)) ## csrsRdata0(31, 0) }
          is(3.U) { wireNum(0) :=                                    csrsRdata0        }
          is(4.U) { wireNum(0) := Fill(XLEN - 8 ,           0.B) ##  csrsRdata0( 7, 0) }
          is(5.U) { wireNum(0) := Fill(XLEN - 16,           0.B) ##  csrsRdata0(15, 0) }
          is(6.U) { wireNum(0) := Fill(XLEN - 32,           0.B) ##  csrsRdata0(31, 0) }
        }
        wireNum(1) := non; wireNum(2) := non; wireNum(3)  := non
        wireOp1_2  := non; wireOp1_3  := non; wireSpecial := non
      }
    }
    is(st) {
      when(isClint.io.addr_out =/= 0xFFF.U) {
        io.csrsR.rcsr(0) := isClint.io.addr_out
        wireCsr(0) := isClint.io.addr_out
        switch(decoded(6)) {
          is(0.U) { wireNum(1) := csrsRdata0(XLEN - 1,  8) ## wireDataRs2( 7, 0) }
          is(1.U) { wireNum(1) := csrsRdata0(XLEN - 1, 16) ## wireDataRs2(15, 0) }
          is(2.U) { wireNum(1) := csrsRdata0(XLEN - 1, 32) ## wireDataRs2(31, 0) }
          is(3.U) { wireNum(1) :=                             wireDataRs2        }
        }
        wireNum(0) := non; wireNum(2) := non; wireNum(3)  := non
        wireOp1_2  := non; wireOp1_3  := 0.U; wireSpecial := csr
      }
    }
    is(jump) {
      io.jmpBch := 1.B
      io.jbAddr := io.input.pc + wireImm
    }
    is(jalr) {
      io.jmpBch := 1.B
      io.jbAddr := (wireImm + wireDataRs1)(XLEN - 1, 1) ## 0.U
    }
    is(branch) {
      when(wireData === 1.U) {
        io.jmpBch := 1.B
        io.jbAddr := io.input.pc + wireImm
      }
    }
    is(csr) {
      io.csrsR.rcsr(0) := wireCsr(0)
      wireCsr(0) := wireInstr(31, 20)
    }
    is(inv) { wireExcept(2) := 1.B } // illegal instruction
    is(mret) {
      io.csrsR.rcsr(0) := csrsAddr.Mepc
      io.csrsR.rcsr(1) := csrsAddr.Mstatus

      wireCsr(0) := csrsAddr.Mstatus

      wireNum(0) := io.csrsR.rdata(1)

      io.jmpBch := 1.B
      io.jbAddr := io.csrsR.rdata(0)(XLEN - 1, 2) ## 0.U(2.W)
    }
  }

  AddException(true, mti)
  AddException(true, mei)
  AddException()

  io.lastVR.READY := io.nextVR.READY && !io.isWait

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    wcsr    := wireCsr
    num     := wireNum
    op1_2   := wireOp1_2
    op1_3   := wireOp1_3
    special := wireSpecial
    instr   := wireInstr
    if (Debug) pc := io.input.pc
  }.elsewhen(io.isWait && io.nextVR.READY) {
    NVALID  := 0.B
    rd      := 0.U
    wcsr    := VecInit(Seq.fill(writeCsrsPort)(0xFFF.U(12.W)))
    num     := VecInit(Seq.fill(4)(0.U))
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
    printf("io.output.num(0)  = %x\n", io.output.num(0) )
    printf("io.output.num(1)  = %x\n", io.output.num(1) )
    printf("io.output.num(2)  = %x\n", io.output.num(2) )
    printf("io.output.num(3)  = %x\n", io.output.num(3) )
    printf("io.output.op1_2   = %d\n", io.output.op1_2  )
    printf("io.output.op1_3   = %d\n", io.output.op1_3  )
    printf("io.output.special = %d\n", io.output.special)
  }

  if (Debug) io.output.debug.pc := pc
}

private class AddException(interrupt: Boolean = false, exceptionCode: Value = usi)
  (implicit param: (IDIO, UInt, UInt, Vec[UInt], Vec[UInt], Vec[Bool])) {
  private val fire = WireDefault(0.B)
  private val code = WireDefault(0.U(XLEN.W))
  if (interrupt) {
    fire := param._1.csrsR.rdata(1)(3) && param._1.csrsR.rdata(2)(exceptionCode) && param._1.csrsR.rdata(7)(exceptionCode)
    code := interrupt.B ## exceptionCode.U((XLEN - 1).W)
  } else for (i <- param._6.indices) when(param._6(i)) { fire := 1.B; code := i.U }
  when(param._1.lastVR.VALID) {
    when(fire) {
      param._1.csrsR.rcsr(5) := csrsAddr.Mtvec
      param._1.jmpBch := 1.B
      if (interrupt) param._2 := int
      param._3 := 0.U
      param._4 := VecInit(csrsAddr.Mepc, csrsAddr.Mcause, csrsAddr.Mtval, csrsAddr.Mstatus)
      param._5 := VecInit(param._1.input.pc, code, param._1.input.instr, param._1.csrsR.rdata(1))

      when(interrupt.B && param._1.csrsR.rdata(5)(0)) { param._1.jbAddr := param._1.csrsR.rdata(5)(XLEN - 1, 2) ## 0.U(2.W) + (exceptionCode * 4).U }
      .otherwise { param._1.jbAddr := param._1.csrsR.rdata(5)(XLEN - 1, 2) ## 0.U(2.W) }
    }
  }
}

private object AddException {
  def apply(interrupt: Boolean = false, exceptionCode: Value = usi)(implicit param: (IDIO, UInt, UInt, Vec[UInt], Vec[UInt], Vec[Bool])): AddException = new AddException(interrupt, exceptionCode)
}

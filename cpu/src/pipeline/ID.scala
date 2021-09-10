package cpu.pipeline

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.component._
import ExecSpecials._
import InstrTypes._
import ExceptionCode._
import cpu.tools._
import cpu._
import cpu.privileged.MstatusBundle

private case class csrsAddr()(implicit val p: Parameters) extends CPUParams with cpu.privileged.CSRsAddr

// instruction decoding module
class ID(implicit p: Parameters) extends YQModule {
  implicit val io = IO(new IDIO)

  val csrsRdata0 = io.csrsR.rdata(0)

  val NVALID     = RegInit(0.B)
  val rd         = RegInit(0.U(5.W))
  val wcsr       = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  val op1_2      = RegInit(0.U(AluTypeWidth.W))
  val op1_3      = RegInit(0.U(AluTypeWidth.W))
  val special    = RegInit(0.U(5.W))
  val instr      = RegInit(0.U(32.W))
  val newPriv    = RegInit(3.U(2.W))
  val blocked    = RegInit(0.B)
  val pc      = if (Debug) RegInit(0.U(alen.W)) else null

  val num = RegInit(VecInit(Seq.fill(4)(0.U(xlen.W))))

  val decoded = ListLookup(
    io.input.instr,
    List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv),
    RVInstr().table
  )

  val wireInstr   = WireDefault(UInt(32.W), io.input.instr)
  val wireSpecial = WireDefault(UInt(5.W), decoded(8))
  val wireType    = WireDefault(7.U(3.W))
  val wireRd      = Wire(UInt(5.W))
  val wireCsr     = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  val wireOp1_2   = WireDefault(UInt(AluTypeWidth.W), decoded(5))
  val wireOp1_3   = WireDefault(UInt(AluTypeWidth.W), decoded(6))
  val wireFunt3   = WireDefault(UInt(3.W), wireInstr(14, 12))
  val wireNum     = WireDefault(VecInit(Seq.fill(4)(0.U(xlen.W))))
  val wireImm     = WireDefault(0.U(xlen.W))
  val wireRs1     = WireDefault(UInt(5.W), wireInstr(19, 15))
  val wireRs2     = WireDefault(UInt(5.W), wireInstr(24, 20))
  val wireDataRs1 = WireDefault(UInt(xlen.W), io.gprsR.rdata(0))
  val wireDataRs2 = WireDefault(UInt(xlen.W), io.gprsR.rdata(1))
  val wireExcept  = WireDefault(VecInit(Seq.fill(16)(0.B)))
  val wireNewPriv = WireDefault(3.U(2.W))
  val wireBlocked = WireDefault(Bool(), blocked)


  val alu1_2   = Module(new SimpleALU)
  val wireData = WireDefault(UInt(xlen.W), alu1_2.io.res.asUInt)
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
  io.csrsR.rcsr     := VecInit(Seq.fill(RegConf.readCsrsPort)(0xFFF.U(12.W)))

  io.csrsR.rcsr(1) := csrsAddr().Mstatus
  io.csrsR.rcsr(2) := csrsAddr().Mie
  io.csrsR.rcsr(7) := csrsAddr().Mip

  io.newPriv    := newPriv

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
    is(i) { wireImm := Fill(xlen - 12, wireInstr(31)) ## wireInstr(31, 20) }
    is(u) { wireImm := Fill(xlen - 32, wireInstr(31)) ## wireInstr(31, 12) ## 0.U(12.W) }
    is(j) { wireImm := Cat(Fill(xlen - 20, wireInstr(31)), wireInstr(19, 12), wireInstr(20), wireInstr(30, 21), 0.B) }
    is(s) { wireImm := Fill(xlen - 12, wireInstr(31)) ## wireInstr(31, 25) ## wireInstr(11, 7) }
    is(b) { wireImm := Cat(Fill(xlen - 12, wireInstr(31)), wireInstr(7), wireInstr(30, 25), wireInstr(11, 8), 0.B) }
    is(c) { wireImm := 0.U((xlen - 5).W) ## wireInstr(19, 15) }
  }

  when(decoded(7) === 1.U) { wireRd := wireInstr(11, 7) }
  .otherwise { wireRd := 0.U }

  val isClint = Module(new IsCLINT)
  isClint.io.addr_in := wireDataRs1 + wireImm

  io.jmpBch := 0.B
  io.jbAddr := 0.U
  private val adder0 = WireDefault(UInt(32.W), io.input.pc)
  private val jbaddr = adder0 + wireImm(alen - 1, 0)
  switch(decoded(8)) {
    is(ld) {
      when(isClint.io.addr_out =/= 0xFFF.U) {
        io.csrsR.rcsr(0) := isClint.io.addr_out
        switch(decoded(6)) {
          is(0.U) { wireNum(0) := Fill(xlen - 8 , csrsRdata0( 7)) ## csrsRdata0( 7, 0) }
          is(1.U) { wireNum(0) := Fill(xlen - 16, csrsRdata0(15)) ## csrsRdata0(15, 0) }
          is(2.U) { wireNum(0) := Fill(xlen - 32, csrsRdata0(31)) ## csrsRdata0(31, 0) }
          is(3.U) { wireNum(0) :=                                    csrsRdata0        }
          is(4.U) { wireNum(0) := Fill(xlen - 8 ,           0.B) ##  csrsRdata0( 7, 0) }
          is(5.U) { wireNum(0) := Fill(xlen - 16,           0.B) ##  csrsRdata0(15, 0) }
          is(6.U) { wireNum(0) := Fill(xlen - 32,           0.B) ##  csrsRdata0(31, 0) }
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
          is(0.U) { wireNum(1) := csrsRdata0(xlen - 1,  8) ## wireDataRs2( 7, 0) }
          is(1.U) { wireNum(1) := csrsRdata0(xlen - 1, 16) ## wireDataRs2(15, 0) }
          is(2.U) { wireNum(1) := csrsRdata0(xlen - 1, 32) ## wireDataRs2(31, 0) }
          is(3.U) { wireNum(1) :=                             wireDataRs2        }
        }
        wireNum(0) := non; wireNum(2) := non; wireNum(3)  := non
        wireOp1_2  := non; wireOp1_3  := 0.U; wireSpecial := csr
      }
    }
    is(jump) {
      io.jmpBch := 1.B
      io.jbAddr := jbaddr
    }
    is(jalr) {
      io.jmpBch := 1.B
      adder0    := wireDataRs1(alen - 1, 0)
      io.jbAddr := jbaddr(alen - 1, 1) ## 0.B
    }
    is(branch) {
      when(wireData === 1.U) {
        io.jmpBch := 1.B
        io.jbAddr := jbaddr
      }
    }
    is(csr) {
      io.csrsR.rcsr(0) := wireCsr(0)
      wireCsr(0) := wireInstr(31, 20)
    }
    is(inv)    { wireExcept(2)  := 1.B } // illegal instruction
    is(ecall)  { wireExcept(11) := 1.B } // environment call from M-mode
    is(ebreak) { wireExcept(3)  := 1.B } // breakpoint
    is(mret) {
      when(io.currentPriv =/= 3.U) { wireExcept(2) := 1.B } // illegal instruction
      .otherwise {
        io.csrsR.rcsr(0) := csrsAddr().Mepc
        wireCsr(0)  := csrsAddr().Mstatus
        wireNum(0)  := io.csrsR.rdata(1)
        wireNewPriv := io.csrsR.rdata(1).asTypeOf(new MstatusBundle).MPP
        io.jmpBch := 1.B
        io.jbAddr := io.csrsR.rdata(0)(alen - 1, 2) ## 0.U(2.W)
      }
    }
    is(sret) { // FIXME: consistency between mstatus and sstatus read & write
      when((io.currentPriv =/= 3.U && io.currentPriv =/= 1.U) || io.csrsR.rdata(1).asTypeOf(new MstatusBundle).TSR) { wireExcept(2) := 1.B } // illegal instruction
      .otherwise {
        io.csrsR.rcsr(0) := csrsAddr().Sepc
        wireCsr(0)  := csrsAddr().Mstatus
        wireNum(0)  := io.csrsR.rdata(1)
        wireNewPriv := io.csrsR.rdata(1).asTypeOf(new MstatusBundle).SPP
        io.jmpBch   := 1.B
        io.jbAddr   := io.csrsR.rdata(0)(alen - 1, 2) ## 0.U(2.W)
      }
    }
    is(fencei) { wireBlocked := 1.B }
  }

  AddException(true, mti); AddException(true, mei); AddException()

  io.lastVR.READY := io.nextVR.READY && !io.isWait && !blocked

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    wcsr    := wireCsr
    num     := wireNum
    op1_2   := wireOp1_2
    op1_3   := wireOp1_3
    special := wireSpecial
    instr   := wireInstr
    newPriv := wireNewPriv
    blocked := wireBlocked
    if (Debug) pc := io.input.pc
  }.elsewhen(io.isWait && io.nextVR.READY) {
    NVALID  := 0.B
    rd      := 0.U
    wcsr    := VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W)))
    num     := VecInit(Seq.fill(4)(0.U))
    op1_2   := 0.U
    op1_3   := 0.U
    special := 0.U
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    NVALID  := 0.B
    blocked := 0.B
  }

  if (Debug) io.output.debug.pc := pc

  private class AddException(interrupt: Boolean = false, exceptionCode: Value = usi) {
    private val fire = WireDefault(0.B)
    private val code = WireDefault(0.U(xlen.W))
    if (interrupt) {
      fire := io.csrsR.rdata(1)(3) && io.csrsR.rdata(2)(exceptionCode) && io.csrsR.rdata(7)(exceptionCode)
      code := interrupt.B ## exceptionCode.U((xlen - 1).W)
    } else for (i <- wireExcept.indices) when(wireExcept(i)) { fire := 1.B; code := i.U }
    when(io.lastVR.VALID) {
      when(fire) {
        io.csrsR.rcsr(5) := csrsAddr().Mtvec
        io.jmpBch := 1.B
        wireSpecial := exception
        wireRd := 0.U
        wireCsr := VecInit(csrsAddr().Mepc, csrsAddr().Mcause, csrsAddr().Mtval, csrsAddr().Mstatus)
        wireNum := VecInit(io.input.pc, code, io.input.instr, io.csrsR.rdata(1))

        when(interrupt.B && io.csrsR.rdata(5)(0)) { io.jbAddr := io.csrsR.rdata(5)(alen - 1, 2) ## 0.U(2.W) + (exceptionCode * 4).U }
        .otherwise { io.jbAddr := io.csrsR.rdata(5)(alen - 1, 2) ## 0.U(2.W) }
      }
    }
  }

  private object AddException {
    def apply(interrupt: Boolean = false, exceptionCode: Value = usi): AddException = new AddException(interrupt, exceptionCode)
  }
}

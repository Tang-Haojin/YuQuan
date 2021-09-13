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
  val io = IO(new IDIO)

  private val idle::loading::Nil = Enum(2)
  private val csrsRdata0 = io.csrsR.rdata(0)

  private val NVALID  = RegInit(0.B)
  private val rd      = RegInit(0.U(5.W))
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val op1_2   = RegInit(0.U(AluTypeWidth.W))
  private val op1_3   = RegInit(0.U(AluTypeWidth.W))
  private val special = RegInit(0.U(5.W))
  private val instr   = RegInit(0.U(32.W))
  private val newPriv = RegInit(3.U(2.W))
  private val blocked = RegInit(0.B)
  private val amoStat = RegInit(UInt(1.W), idle)
  private val retire  = RegInit(0.B)
  private val pc      = if (Debug) RegInit(0.U(alen.W)) else null

  private val num = RegInit(VecInit(Seq.fill(4)(0.U(xlen.W))))

  private val decoded = ListLookup(
    io.input.instr,
    List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv),
    RVInstr().table
  )

  private val wireInstr   = WireDefault(UInt(32.W), io.input.instr)
  private val wireSpecial = WireDefault(UInt(5.W), decoded(8))
  private val wireType    = WireDefault(7.U(3.W))
  private val wireRd      = Wire(UInt(5.W))
  private val wireCsr     = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val wireOp1_2   = WireDefault(UInt(AluTypeWidth.W), decoded(5))
  private val wireOp1_3   = WireDefault(UInt(AluTypeWidth.W), decoded(6))
  private val wireNum     = WireDefault(VecInit(Seq.fill(4)(0.U(xlen.W))))
  private val wireImm     = WireDefault(0.U(xlen.W))
  private val wireRs1     = WireDefault(UInt(5.W), wireInstr(19, 15))
  private val wireRs2     = WireDefault(UInt(5.W), wireInstr(24, 20))
  private val wireDataRs1 = WireDefault(UInt(xlen.W), io.gprsR.rdata(0))
  private val wireDataRs2 = WireDefault(UInt(xlen.W), io.gprsR.rdata(1))
  private val wireExcept  = WireDefault(VecInit(Seq.fill(16)(0.B)))
  private val wireNewPriv = WireDefault(3.U(2.W))
  private val wireAmoStat = WireDefault(UInt(1.W), amoStat)
  private val wireRetire  = WireDefault(Bool(), 1.B)
  private val wireBlocked = WireDefault(Bool(), blocked)

  private val alu1_2   = Module(new SimpleALU)
  private val wireData = WireDefault(UInt(xlen.W), alu1_2.io.res.asUInt)
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
  io.output.retire  := retire
  io.isAmo          := amoStat =/= idle
  io.gprsR.raddr    := VecInit(10.U, 0.U, 0.U)
  io.csrsR.rcsr     := VecInit(Seq.fill(RegConf.readCsrsPort)(0xFFF.U(12.W)))

  io.csrsR.rcsr(1) := csrsAddr().Mstatus
  io.csrsR.rcsr(2) := csrsAddr().Mie
  io.csrsR.rcsr(7) := csrsAddr().Mip

  io.newPriv := newPriv
  for (i <- 1 to 4) when(decoded(i) === NumTypes.rs1) { io.gprsR.raddr(0) := wireRs1 }
  .elsewhen(decoded(i) === NumTypes.rs2) { io.gprsR.raddr(1) := wireRs2 }

  for (i <- wireNum.indices) wireNum(i) := MuxLookup(decoded(i + 1), 0.U, Seq(
    NumTypes.rs1  -> wireDataRs1,
    NumTypes.rs2  -> wireDataRs2,
    NumTypes.imm  -> wireImm,
    NumTypes.four -> 4.U,
    NumTypes.pc   -> io.input.pc,
    NumTypes.non  -> 0.U,
    NumTypes.csr  -> io.csrsR.rdata(0)
  ))

  wireImm := MuxLookup(decoded.head, 0.U, Seq(
    i -> Fill(xlen - 12, wireInstr(31)) ## wireInstr(31, 20),
    u -> Fill(xlen - 32, wireInstr(31)) ## wireInstr(31, 12) ## 0.U(12.W),
    j -> Cat(Fill(xlen - 20, wireInstr(31)), wireInstr(19, 12), wireInstr(20), wireInstr(30, 21), 0.B),
    s -> Fill(xlen - 12, wireInstr(31)) ## wireInstr(31, 25) ## wireInstr(11, 7),
    b -> Cat(Fill(xlen - 12, wireInstr(31)), wireInstr(7), wireInstr(30, 25), wireInstr(11, 8), 0.B),
    c -> 0.U((xlen - 5).W) ## wireInstr(19, 15)
  ))

  wireRd := Mux(decoded(7) === 1.U, wireInstr(11, 7), 0.U)

  private val isClint = Module(new IsCLINT)
  isClint.io.addr_in := wireDataRs1 + wireImm

  io.jmpBch := 0.B; io.jbAddr := 0.U
  private val adder0 = WireDefault(UInt(32.W), io.input.pc)
  private val jbaddr = adder0 + wireImm(alen - 1, 0)

  when(decoded(8) === ld && isClint.io.addr_out =/= 0xFFF.U) {
    io.csrsR.rcsr(0) := isClint.io.addr_out
    wireNum(0) := MuxLookup(decoded(6), 0.U, Seq(
      0.U -> Fill(xlen - 8 , csrsRdata0( 7)) ## csrsRdata0( 7, 0),
      1.U -> Fill(xlen - 16, csrsRdata0(15)) ## csrsRdata0(15, 0),
      2.U -> Fill(xlen - 32, csrsRdata0(31)) ## csrsRdata0(31, 0),
      3.U ->                                    csrsRdata0       ,
      4.U -> Fill(xlen - 8 ,            0.B) ## csrsRdata0( 7, 0),
      5.U -> Fill(xlen - 16,            0.B) ## csrsRdata0(15, 0),
      6.U -> Fill(xlen - 32,            0.B) ## csrsRdata0(31, 0)
    ))
    wireNum(1) := non; wireNum(2) := non; wireNum(3)  := non
    wireOp1_2  := non; wireOp1_3  := non; wireSpecial := non
  }
  when(decoded(8) === st && isClint.io.addr_out =/= 0xFFF.U) {
    io.csrsR.rcsr(0) := isClint.io.addr_out
    wireCsr(0) := isClint.io.addr_out
    wireNum(1) := MuxLookup(decoded(6), 0.U, Seq(
      0.U -> csrsRdata0(xlen - 1,  8) ## wireDataRs2( 7, 0),
      1.U -> csrsRdata0(xlen - 1, 16) ## wireDataRs2(15, 0),
      2.U -> csrsRdata0(xlen - 1, 32) ## wireDataRs2(31, 0),
      3.U ->                             wireDataRs2
    ))
    wireNum(0) := non; wireNum(2) := non; wireNum(3)  := non
    wireOp1_2  := non; wireOp1_3  := 0.U; wireSpecial := csr
  }
  when(decoded(8) === jump || (decoded(8) === branch && wireData === 1.U)) {
    io.jmpBch := 1.B
    io.jbAddr := jbaddr
  }
  when(decoded(8) === jalr) {
    io.jmpBch := 1.B
    adder0    := wireDataRs1(alen - 1, 0)
    io.jbAddr := jbaddr(alen - 1, 1) ## 0.B
  }
  when(decoded(8) === csr) {
    io.csrsR.rcsr(0) := wireCsr(0)
    wireCsr(0) := wireInstr(31, 20)
  }
  when(decoded(8) === inv)    { wireExcept(2)  := 1.B } // illegal instruction
  when(decoded(8) === ecall)  { wireExcept(11) := 1.B } // environment call from M-mode
  when(decoded(8) === ebreak) { wireExcept(3)  := 1.B } // breakpoint
  when(decoded(8) === fencei) { wireBlocked    := 1.B }
  when(decoded(8) === mret) {
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
  when(decoded(8) === sret) { // FIXME: consistency between mstatus and sstatus read & write
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
  if (extensions.contains('A')) when(decoded(8) === amo && amoStat === idle && wireOp1_2 =/= Operators.lr && wireOp1_2 =/= Operators.sc) {
    wireAmoStat := loading
    wireSpecial := ld
    wireRetire  := 0.B
  }

  AddException(true, mti); AddException(true, mei); AddException()

  io.lastVR.READY := io.nextVR.READY && !io.isWait && !blocked && amoStat === idle

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
    if (extensions.contains('A')) amoStat := wireAmoStat
    retire  := wireRetire
    if (Debug) pc := io.input.pc
  }.elsewhen(io.isWait && io.nextVR.READY && amoStat === idle) {
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

  if (extensions.contains('A')) when(amoStat === loading) {
    io.gprsR.raddr(0) := rd
    when(!io.isWait) {
      num(1)  := io.gprsR.rdata(0)
      special := st
      NVALID  := 1.B
      amoStat := idle
      retire  := 1.B
      rd      := 0.U
    }
  }

  if (Debug) io.output.debug.pc := pc

  private class AddException(interrupt: Boolean = false, exceptionCode: Value = usi) {
    private val fire = WireDefault(0.B)
    private val code = WireDefault(0.U(xlen.W))
    if (interrupt) {
      fire := io.csrsR.rdata(1)(3) && io.csrsR.rdata(2)(exceptionCode) && io.csrsR.rdata(7)(exceptionCode)
      code := interrupt.B ## exceptionCode.U((xlen - 1).W)
    } else for (i <- wireExcept.indices) when(wireExcept(i)) { fire := 1.B; code := i.U }
    when(io.lastVR.VALID && fire) {
      io.csrsR.rcsr(5) := csrsAddr().Mtvec
      io.jmpBch := 1.B
      wireSpecial := exception
      wireRd := 0.U
      wireCsr := VecInit(csrsAddr().Mepc, csrsAddr().Mcause, csrsAddr().Mtval, csrsAddr().Mstatus)
      wireNum := VecInit(io.input.pc, code, io.input.instr, io.csrsR.rdata(1))
      io.jbAddr := io.csrsR.rdata(5)(alen - 1, 2) ## 0.U(2.W) + Mux(interrupt.B && io.csrsR.rdata(5)(0), (exceptionCode * 4).U, 0.U)
    }
  }

  private object AddException {
    def apply(interrupt: Boolean = false, exceptionCode: Value = usi): AddException = new AddException(interrupt, exceptionCode)
  }
}

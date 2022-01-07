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
import cpu.privileged._

// instruction decoding module
class ID(implicit p: Parameters) extends YQModule {
  val io = IO(new IDIO)

  private class csrsAddr(implicit val p: Parameters) extends CPUParams with cpu.privileged.CSRsAddr
  private val csrsAddr = new csrsAddr
  private val idle::loading::Nil = Enum(2)
  private val memExcept = Seq(/*5,7,*/13,15,4,6)

  private val csrr    = io.csrsR.rdata(0)
  private val mstatus = io.csrsR.rdata(1).asTypeOf(new MstatusBundle)
  private val mie     = io.csrsR.rdata(2).asTypeOf(new MieBundle)
  private val mideleg = io.csrsR.rdata(3).asTypeOf(new MidelegBundle)
  private val medeleg = io.csrsR.rdata(4).asTypeOf(new MedelegBundle)
  private val mtvec   = io.csrsR.rdata(5)
  private val stvec   = io.csrsR.rdata(6)
  private val mip     = WireDefault(new MipBundle, io.csrsR.rdata(7).asTypeOf(new MipBundle))
  mip.MTIP := io.mtip; mip.MSIP := io.msip
  private val mepc    = io.csrsR.rdata(8)
  private val sepc    = io.csrsR.rdata(9)

  private val NVALID  = RegInit(0.B)
  private val rd      = RegInit(0.U(5.W))
  private val pc      = RegInit(0.U(valen.W))
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val isWcsr  = RegInit(0.B)
  private val op1_2   = RegInit(0.U(Operators.quantity.W))
  private val op1_3   = RegInit(0.U(Operators.quantity.W))
  private val special = RegInit(0.U(5.W))
  private val instr   = RegInit(0.U(32.W))
  private val newPriv = RegInit(3.U(2.W))
  private val isPriv  = RegInit(0.B)
  private val blocked = RegInit(0.B)
  private val amoStat = RegInit(UInt(1.W), idle)
  private val retire  = RegInit(0.B)
  private val isSatp  = RegInit(0.B)
  private val except  = RegInit(0.B)
  private val cause   = RegInit(0.U(4.W))
  private val jmpBch  = RegInit(0.B)
  private val jbAddr  = RegInit(0.U(valen.W))
  private val jbPend  = RegInit(0.B)
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val intr    = if (Debug) RegInit(0.B) else null
  private val rvc     = if (Debug) RegInit(0.B) else null

  private val num = RegInit(VecInit(Seq.fill(4)(0.U(xlen.W))))

  private val decoded = RVInstrDecoder(io.input.instr)

  private val wireInstr   = io.input.instr
  private val wireFunct3c = io.input.instr(15, 13)
  private val wireSpecial = WireDefault(UInt(5.W), decoded(7))
  private val wireType    = WireDefault(7.U(3.W))
  private val wireRd      = Wire(UInt(5.W))
  private val wireIsWcsr  = WireDefault(0.B)
  private val wireCsr     = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val wireOp1_2   = WireDefault(UInt(Operators.quantity.W), decoded(5))
  private val wireNum     = WireDefault(VecInit(Seq.fill(4)(0.U(xlen.W))))
  private val wireImm     = WireDefault(0.U(xlen.W))
  private val wireRs1     = WireDefault(UInt(5.W), io.input.rs(0))
  private val wireRs2     = WireDefault(UInt(5.W), io.input.rs(1))
  private val wireRs1c    = wireInstr(11, 7)
  private val wireRs2c    = wireInstr(6, 2)
  private val wireRs1p    = 1.B ## wireInstr(9, 7)
  private val wireRs2p    = 1.B ## wireInstr(4, 2)
  private val wireRd1c    = wireRs1c
  private val wireRd1p    = wireRs1p
  private val wireExcept  = WireDefault(VecInit(Seq.fill(32)(0.B)))
  private val wirePriv    = WireDefault(UInt(2.W), io.currentPriv)
  private val wireIsPriv  = WireDefault(0.B)
  private val wireAmoStat = WireDefault(UInt(1.W), amoStat)
  private val wireRetire  = WireDefault(Bool(), 1.B)
  private val wireBlocked = WireDefault(Bool(), blocked)
  private val wireIsSatp  = WireDefault(0.B)
  private val wireIntr    = if (Debug) WireDefault(0.B) else null

  private val isMemExcept = VecInit(memExcept.map(wireExcept(_))).asUInt.orR

  private val lessthan  = io.gprsR.rdata(0).asSInt <   io.gprsR.rdata(1).asSInt
  private val ulessthan = io.gprsR.rdata(0)        <   io.gprsR.rdata(1)
  private val equal     = io.gprsR.rdata(0)        === io.gprsR.rdata(1)
  private val willBranch = Mux(!ext('C').B || wireInstr(1, 0).andR, MuxLookup(wireInstr(14, 12), equal, Seq(
    "b001".U -> !equal,
    "b100".U -> lessthan,
    "b101".U -> !lessthan,
    "b110".U -> ulessthan,
    "b111".U -> !ulessthan
  )), Mux(wireFunct3c(1, 0) === "b10".U, equal, !equal))

  io.nextVR.VALID   := NVALID
  io.output.rd      := rd
  io.output.isWcsr  := isWcsr
  io.output.wcsr    := wcsr
  io.output.num     := num
  io.output.op1_2   := op1_2
  io.output.op1_3   := op1_3
  io.output.special := special
  io.output.retire  := retire
  io.isAmo          := amoStat =/= idle
  io.output.priv    := newPriv
  io.output.isPriv  := isPriv
  io.output.isSatp  := isSatp
  io.output.except  := except
  io.output.cause   := cause
  io.output.pc      := pc
  io.gprsR.raddr    := VecInit(0.U, 0.U, 0.U)
  io.csrsR.rcsr     := VecInit(Seq.fill(RegConf.readCsrsPort)(0xFFF.U(12.W)))

  io.csrsR.rcsr(0) := wireInstr(31, 20)
  io.csrsR.rcsr(1) := csrsAddr.Mstatus
  io.csrsR.rcsr(2) := csrsAddr.Mie
  io.csrsR.rcsr(3) := csrsAddr.Mideleg
  io.csrsR.rcsr(4) := csrsAddr.Medeleg
  io.csrsR.rcsr(5) := csrsAddr.Mtvec
  if (ext('S')) io.csrsR.rcsr(6) := csrsAddr.Stvec
  io.csrsR.rcsr(7) := csrsAddr.Mip
  io.csrsR.rcsr(8) := csrsAddr.Mepc
  if (ext('S')) io.csrsR.rcsr(9) := csrsAddr.Sepc

  private def hasNumType(numType: UInt): Bool = VecInit(decoded.slice(1, 5).map(_ === numType)).asUInt.orR
  private val hasRsType = Seq(NumTypes.rs1, NumTypes.rs2, NumTypes.rs1c, NumTypes.rs2c, NumTypes.rs1p, NumTypes.rs2p, NumTypes.rd1c, NumTypes.rd1p, NumTypes.x2).map(x => x -> hasNumType(x)).toMap
  io.gprsR.raddr(0) := (if (ext('C')) MuxCase(0.U, Seq(
    hasRsType(NumTypes.rs1)  -> wireRs1,
    hasRsType(NumTypes.rs1c) -> wireRs1c,
    hasRsType(NumTypes.rs1p) -> wireRs1p,
    hasRsType(NumTypes.rd1c) -> Mux(decoded.head === clui && wireRs1c =/= "b00010".U, 0.U, wireRd1c),
    hasRsType(NumTypes.rd1p) -> wireRd1p,
    hasRsType(NumTypes.x2)   -> 2.U
  )) else wireRs1)
  io.gprsR.raddr(1) := (if (ext('C')) MuxCase(0.U, Seq(
    hasRsType(NumTypes.rs2)  -> wireRs2,
    hasRsType(NumTypes.rs2c) -> wireRs2c,
    hasRsType(NumTypes.rs2p) -> wireRs2p
  )) else wireRs2)
  io.gprsR.raddr(2) := (if (ext('C')) Mux(io.input.instrCode === "b1100111".U, wireRs1, wireInstr(11, 7)) else wireRs1)
  private val useRaddr2 = (io.input.instrCode === "b1100111".U) || (ext('C').B && io.input.instrCode === "b10".U && wireFunct3c === "b100".U && wireInstr(11, 7) =/= 0.U)
  private val isCJR = io.input.instrCode === "b0000010".U && wireFunct3c === "b100".U

  for (i <- wireNum.indices) wireNum(i) := Mux1H(decoded(i + 1), (Seq(
    /* non  */ 0.U,
    /* rs1  */ io.gprsR.rdata(0),
    /* rs2  */ io.gprsR.rdata(1),
    /* imm  */ wireImm,
    /* four */ 4.U,
    /* pc   */ io.input.pc) ++ (if (!isZmb) Seq(
    /* csr  */ io.csrsR.rdata(0)) else Nil) ++ (if (ext('C')) Seq(
    /* rs1c */ io.gprsR.rdata(0),
    /* rs2c */ Mux(isCJR, 2.U, io.gprsR.rdata(1)),
    /* rs1p */ io.gprsR.rdata(0),
    /* rs2p */ io.gprsR.rdata(1),
    /* rd1c */ Mux(isCJR, io.input.pc, io.gprsR.rdata(0)),
    /* rd1p */ io.gprsR.rdata(0),
    /* x2   */ io.gprsR.rdata(0)) ++ (if (xlen == 32) Seq(
    /* two  */ 2.U) else Nil) else Nil)))

  private val immMap = Map(
    i       -> Fill(xlen - 12, wireInstr(31)) ## wireInstr(31, 20),
    u       -> Fill(xlen - 32, wireInstr(31)) ## wireInstr(31, 12) ## 0.U(12.W),
    j       -> Cat(Fill(xlen - 20, wireInstr(31)), wireInstr(19, 12), wireInstr(20), wireInstr(30, 21), 0.B),
    s       -> Fill(xlen - 12, wireInstr(31)) ## wireInstr(31, 25) ## wireInstr(11, 7),
    b       -> Cat(Fill(xlen - 12, wireInstr(31)), wireInstr(7), wireInstr(30, 25), wireInstr(11, 8), 0.B)) ++ (if (!isZmb) Map(
    c       -> 0.U((xlen - 5).W) ## wireInstr(19, 15)) else Nil) ++ (if (ext('C')) Map(
    cinv    -> 0.U(xlen.W),
    cni     -> 0.U(xlen.W),
    caddi4  -> 0.U((xlen - 10).W) ## wireInstr(10, 7) ## wireInstr(12, 11) ## wireInstr(5) ## wireInstr(6) ## 0.U(2.W),
    cldst   -> 0.U((xlen - 8).W) ## Mux(wireFunct3c(1, 0) === "b10".U, 0.B ## wireInstr(5) ## wireInstr(12, 10) ## wireInstr(6), wireInstr(6, 5) ## wireInstr(12, 10) ## 0.B) ## 0.U(2.W),
    c540    -> Fill(xlen - 5, wireInstr(12)) ## wireInstr(6, 2),
    clui    -> Mux(wireRs1c =/= "b00010".U, Fill(xlen - 17, wireInstr(12)) ## wireInstr(6, 2) ## 0.U(12.W),
               Fill(xlen - 9, wireInstr(12)) ## wireInstr(4, 3) ## wireInstr(5) ## wireInstr(2) ## wireInstr(6) ## 0.U(4.W)),
    cj      -> Fill(xlen - 11, wireInstr(12)) ## wireInstr(8) ## wireInstr(10, 9) ## wireInstr(6) ## wireInstr(7) ## wireInstr(2) ## wireInstr(11) ## wireInstr(5, 3) ## 0.B,
    cb      -> Fill(xlen - 8, wireInstr(12)) ## wireInstr(6, 5) ## wireInstr(2) ## wireInstr(11, 10) ## wireInstr(4, 3) ## 0.B,
    clsp    -> 0.U((xlen - 9).W) ## Mux(wireFunct3c(1, 0) === "b10".U, 0.B ## wireInstr(3, 2) ## wireInstr(12) ## wireInstr(6, 4), wireInstr(4, 2) ## wireInstr(12) ## wireInstr(6, 5) ## 0.B) ## 0.U(2.W),
    cssp    -> 0.U((xlen - 9).W) ## Mux(wireFunct3c(1, 0) === "b10".U, 0.B ## wireInstr(8, 7) ## wireInstr(12, 9), wireInstr(9, 7) ## wireInstr(12, 10) ## 0.B) ## 0.U(2.W)
  ) else Nil)
  private val wireCRd = (if (ext('C')) MuxCase(io.input.rd, Seq(
    hasRsType(NumTypes.rd1p)                            -> wireRd1p,
    (decoded.head === caddi4 || decoded.head === cldst) -> wireRs2p,
    isCJR                                               -> wireInstr(12)) ++ (if (xlen == 32) Seq(
    (decoded.head === cj)                               -> 1.U
  ) else Nil)) else 0.U)
  wireImm := Mux1H(immMap.map(x => (decoded.head === x._1(RVInstr().instrTypeNum - 1, 0), x._2)))
  wireRd := Fill(5, decoded(6)(0)) & Mux(wireInstr(1, 0).andR || !ext('C').B, io.input.rd, wireCRd)

  io.jmpBch := jmpBch; io.jbAddr := jbAddr
  private val jbCOffset = if (ext('C')) MuxLookup(wireFunct3c(1, 0), immMap(cb), Seq("b01".U -> immMap(cj), "b00".U -> 0.U)) else 0.U
  private val jbOffset  = Mux(wireInstr(1, 0).andR || !ext('C').B, MuxLookup(io.input.instrCode(3, 2), immMap(j), Seq("b01".U -> immMap(i), "b00".U -> immMap(b))), jbCOffset)
  private val tmpJbaddr = Mux(useRaddr2, io.gprsR.rdata(2)(valen - 1, 1), io.input.pc(valen - 1, 1)) + jbOffset(valen - 1, 1)
  private val wireJbAddr = WireDefault(UInt(valen.W), tmpJbaddr ## 0.B)

  private val instrJump = io.input.instrCode === "b1101111".U || (ext('C').B && (wireInstr(1, 0) === "b01".U && (wireFunct3c === "b101".U || (xlen == 32).B && wireFunct3c === "b001".U)))
  private val instrBranch = io.input.instrCode === "b1100011".U || (ext('C').B && wireInstr(1, 0) === "b01".U && wireFunct3c(2, 1) === "b11".U)
  private val instrJalr = io.input.instrCode === "b1100111".U || (ext('C').B && isCJR)
  private val wireJmpBch = WireDefault(Bool(), instrJump || instrJalr || (instrBranch && willBranch))
  private val isZicsr = io.input.instrCode === "b1110011".U && wireInstr(13, 12) =/= "b00".U
  if (!isZmb) when(isZicsr) {
    when(ext('S').B && (wireInstr(31, 28) === "b0000".U /*fpu*/|| wireInstr(31, 25) === "b0011101".U /*pmp*/)) {
      wireExcept(2) := 1.B
    }.otherwise {
      wireSpecial := zicsr
      wireIsWcsr := 1.B
      wireCsr(0) := wireInstr(31, 20)
      if (ext('S')) when(wireCsr(0) === csrsAddr.Satp) { wireIsSatp := 1.B }
    }
  }
  if (!isZmb) when(decoded(7) === inv) { wireExcept(2)  := 1.B } // illegal instruction
  if (!isZmb) when(decoded(7) === ecall) {
    when(io.currentPriv === "b11".U) { wireExcept(11) := 1.B } // environment call from M-mode
    if (ext('S')) when(io.currentPriv === "b01".U) { wireExcept( 9) := 1.B } // environment call from S-mode
    if (ext('U')) when(io.currentPriv === "b00".U) { wireExcept( 8) := 1.B } // environment call from U-mode
  }
  if (!isZmb) when(decoded(7) === ebreak || (ext('C').B && io.input.instr(15, 0) === "b1001000000000010".U)) { wireExcept(3) := 1.B } // breakpoint
  if (!isZmb) when(decoded(7) === fencei) { wireBlocked := 1.B }
  if (!isZmb) when(decoded(7) === mret) {
    when(io.currentPriv =/= 3.U) { wireExcept(2) := 1.B } // illegal instruction
    .otherwise {
      wireIsWcsr := 1.B
      wireCsr(0) := csrsAddr.Mstatus
      wireNum(0) := io.csrsR.rdata(1)
      if (ext('S') || ext('U')) wirePriv := mstatus.MPP
      wireJmpBch := 1.B
      wireJbAddr := mepc
    }
  }
  if (ext('S')) when(decoded(7) === sret) {
    when((io.currentPriv =/= 3.U && io.currentPriv =/= 1.U) || mstatus.TSR) { wireExcept(2) := 1.B } // illegal instruction
    .otherwise {
      wireIsWcsr := 1.B
      wireCsr(0) := csrsAddr.Mstatus
      wireNum(0) := io.csrsR.rdata(1)
      wirePriv   := mstatus.SPP
      wireJmpBch := 1.B
      wireJbAddr := sepc
    }
  }
  if (ext('S')) when(decoded(7) === sfence) { wireIsSatp := 1.B }
  if (ext('A')) {
    when(decoded(7) === amo && amoStat === idle && wireOp1_2 =/= Operators.lr && wireOp1_2 =/= Operators.sc) {
      wireAmoStat := loading
      wireSpecial := ld
      wireRetire  := 0.B
    }
    when(amoStat === loading) {
      io.gprsR.raddr(0) := rd
      when(!io.isWait && io.nextVR.READY) {
        num(1)  := io.gprsR.rdata(0)
        special := st
        NVALID  := 1.B
        amoStat := idle
        retire  := 1.B
        rd      := 0.U
      }
    }
    when(io.revAmo) { amoStat := idle }
  }
  if (isZmb) when(decoded(7) === trap) {
    wireSpecial := norm
    when(io.nextVR.READY && io.nextVR.VALID) {
      when(io.gprsR.rdata(0) === 0.U) { printf("HIT GOOD TRAP\n") }
      .otherwise { printf("HIT BAD TRAP\n") }
      assert(0.B)
    }
  }

  when(io.input.except) { wireExcept(io.input.cause) := 1.B }
  if (!isZmb) HandleException()

  io.lastVR.READY := io.nextVR.READY && !io.isWait && !blocked && amoStat === idle

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    when(!jbPend || jbAddr === io.input.pc || isMemExcept) {
      NVALID     := 1.B
      rd         := wireRd
      isWcsr     := wireIsWcsr
      wcsr       := wireCsr
      num        := wireNum
      op1_2      := wireOp1_2
      op1_3      := Mux(wireInstr(1, 0).andR || !ext('C').B, wireInstr(14, 12), 0.B ## wireInstr(14, 13))
      special    := wireSpecial
      instr      := wireInstr
      wireIsPriv := wirePriv =/= io.currentPriv
      newPriv    := wirePriv
      isPriv     := wireIsPriv
      blocked    := wireBlocked
      isSatp     := wireIsSatp
      if (ext('A')) amoStat := wireAmoStat
      retire     := wireRetire
      except     := io.input.except
      cause      := io.input.cause
      pc         := io.input.pc
      jbPend     := 0.B
      jbAddr     := wireJbAddr
      when(wireJmpBch && wireJbAddr =/= io.input.pc + Mux(wireInstr(1, 0).andR || !ext('C').B, 4.U, 2.U)) { jmpBch := 1.B; jbPend := 1.B }
      if (Debug) {
        rcsr := Mux(wireSpecial === zicsr, wireInstr(31, 20), 0xfff.U)
        intr := wireIntr
        rvc  := !wireInstr(1, 0).andR
      }
    }.otherwise { NVALID := 0.B }
  }.otherwise {
    when(io.isWait && io.nextVR.READY && amoStat === idle) {
      NVALID  := 0.B
      rd      := 0.U
      isWcsr  := 0.B
      wcsr    := VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W)))
      num     := VecInit(Seq.fill(4)(0.U))
      op1_2   := 0.U
      op1_3   := 0.U
      special := 0.U
    }
    when(io.nextVR.READY && io.nextVR.VALID) {
      NVALID  := 0.B
      blocked := 0.B
      isSatp  := 0.B
    }
  }

  when(jmpBch) { jmpBch := 0.B }

  if (Debug) {
    io.output.debug.rcsr := rcsr
    io.output.debug.intr := intr
    io.output.debug.priv := newPriv
    io.output.debug.rvc  := rvc
  }

  private case class HandleException() {
    private val fire = WireDefault(0.B)
    private val code = WireDefault(0.U(xlen.W))
    private val intCode = WireDefault("b1111".U(4.W))
    private val isInt = intCode =/= "b1111".U
    private val tmpNewPriv = WireDefault(UInt(2.W), newPriv)
    when(amoStat === idle) {
      if (ext('U')) Seq(uti, usi, uei).foreach(x => {
        when(mstatus.UIE && io.currentPriv === "b00".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(io.currentPriv === "b11".U && !mideleg(x) && mie(x) && mip(x)) { intCode := x.id.U }
      })
      if (ext('S')) Seq(sti, ssi, sei).foreach(x => {
        if (ext('U')) when(io.currentPriv === "b00".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(mstatus.SIE && io.currentPriv === "b01".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(io.currentPriv === "b11".U && !mideleg(x) && mie(x) && mip(x)) { intCode := x.id.U }
      })
      Seq(mti, msi, mei).foreach(x => {
        when(io.currentPriv =/= "b11".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(mstatus.MIE && io.currentPriv === "b11".U && mie(x) && mip(x)) { intCode := x.id.U }
      })
      when(isMemExcept) {
        // the mem-access exceptions should be handled first, because they happened "in the past".
        // All other types of exceptions prior to these must not have happened, as the mem-access
        // action can not have been acted if any other exceptions had occurred in previous id level.
        memExcept.filter(_ != (if (!ext('S')) 13 else 32)).filter(_ != (if (!ext('S')) 15 else 32)).foreach(i => when(wireExcept(i)) { fire := 1.B; code := i.U })
        when(!io.currentPriv(1)) { tmpNewPriv := Mux(medeleg(code), "b01".U, "b11".U) }
      }.elsewhen(isInt) {
        fire := 1.B
        code := 1.B ## 0.U((xlen - 5).W) ## intCode
        when(!io.currentPriv(1)) { tmpNewPriv := Mux(mideleg(intCode), "b01".U, "b11".U) }
        if (Debug) wireIntr := 1.B
      }.otherwise {
        Seq(/*24,*/3,8,9,11,0,2,/*1,*/12/*,25*/).filter(_ != (if (ext('C')) 0 else 32)).filter(_ != (if (!ext('S')) 9 else 32)).filter(_ != (if (!ext('U')) 8 else 32)).filter(_ != (if (!ext('S')) 12 else 32))
        .foreach(i => when(wireExcept(i)) { fire := 1.B; code := i.U }) // TODO: 24 for watchpoint, and 25 for breakpoint
        when(!io.currentPriv(1)) { tmpNewPriv := Mux(medeleg(code), "b01".U, "b11".U) } // TODO: user interrupt
      }
      when(io.lastVR.VALID && fire) {
        val mstat   = io.csrsR.rdata(1)(xlen - 1) ## io.currentPriv ## wirePriv ## io.csrsR.rdata(1)(xlen - 6, 0)
        val bad     = (if (ext('C')) 0.B else code === 0.U) | code === 1.U | code === 2.U | code === 3.U | code === 12.U
        val badAddr = Mux(code === 2.U, Mux(wireInstr(1, 0).andR, wireInstr, wireInstr(15, 0)), Mux(io.input.crossCache && ext('C').B, io.input.pc + 2.U, io.input.pc))
        val Xepc    = Mux1H((Seq("b11".U -> csrsAddr.Mepc) ++ (if (ext('S')) Seq("b01".U -> csrsAddr.Sepc) else Nil) ++ (if (ext('U')) Seq("b00".U -> csrsAddr.Uepc) else Nil)).map(x => (wirePriv === x._1, x._2)))
        val Xcause  = Mux1H((Seq("b11".U -> csrsAddr.Mcause) ++ (if (ext('S')) Seq("b01".U -> csrsAddr.Scause) else Nil) ++ (if (ext('U')) Seq("b00".U -> csrsAddr.Ucause) else Nil)).map(x => (wirePriv === x._1, x._2)))
        val Xtval   = Mux1H((Seq("b11".U -> csrsAddr.Mtval) ++ (if (ext('S')) Seq("b01".U -> csrsAddr.Stval) else Nil) ++ (if (ext('U')) Seq("b00".U -> csrsAddr.Utval) else Nil)).map(x => (wirePriv === x._1, x._2)))
        val mjaddr  = Mux(isInt && mtvec(0), mtvec(valen - 1, 2) + code(3, 0), mtvec(valen - 1, 2)) ## 0.U(2.W)
        val sjaddr  = Mux(isInt && stvec(0), stvec(valen - 1, 2) + code(3, 0), stvec(valen - 1, 2)) ## 0.U(2.W)
        wireJmpBch := 1.B
        if (ext('S') || ext('U')) wirePriv := tmpNewPriv
        wireSpecial := exception
        wireCsr := VecInit(Xepc, Xcause, Xtval, csrsAddr.Mstatus)
        wireNum := VecInit(io.input.pc, code, Mux(bad, badAddr, 0.U), mstat)
        wireJbAddr := Mux(wirePriv === "b11".U, mjaddr, sjaddr) // TODO: user mode interrupt
        wireIsSatp := 0.B; wireBlocked := 0.B; wireAmoStat := amoStat; wireRetire := 1.B
      }
    }
  }
}

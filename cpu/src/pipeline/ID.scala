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

  private val csrr    = io.csrsR.rdata(0)
  private val mstatus = io.csrsR.rdata(1).asTypeOf(new MstatusBundle)
  private val mie     = io.csrsR.rdata(2).asTypeOf(new MieBundle)
  private val mideleg = io.csrsR.rdata(3).asTypeOf(new MidelegBundle)
  private val medeleg = io.csrsR.rdata(4).asTypeOf(new MieBundle)
  private val xtvec   = io.csrsR.rdata(5)
  private val mip     = io.csrsR.rdata(7).asTypeOf(new MipBundle)

  private val NVALID  = RegInit(0.B)
  private val rd      = RegInit(0.U(5.W))
  private val pc      = RegInit(0.U(valen.W))
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val isWcsr  = RegInit(0.B)
  private val op1_2   = RegInit(0.U(AluTypeWidth.W))
  private val op1_3   = RegInit(0.U(AluTypeWidth.W))
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
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val intr    = if (Debug) RegInit(0.B) else null

  private val num = RegInit(VecInit(Seq.fill(4)(0.U(xlen.W))))

  private val decoded = ListLookup(io.input.instr, List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv), RVInstr().table)

  private val wireInstr   = WireDefault(UInt(32.W), io.input.instr)
  private val wireSpecial = WireDefault(UInt(5.W), decoded(7))
  private val wireType    = WireDefault(7.U(3.W))
  private val wireRd      = Wire(UInt(5.W))
  private val wireIsWcsr  = WireDefault(0.B)
  private val wireCsr     = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val wireOp1_2   = WireDefault(UInt(AluTypeWidth.W), decoded(5))
  private val wireNum     = WireDefault(VecInit(Seq.fill(4)(0.U(xlen.W))))
  private val wireImm     = WireDefault(0.U(xlen.W))
  private val wireRs1     = WireDefault(UInt(5.W), wireInstr(19, 15))
  private val wireRs2     = WireDefault(UInt(5.W), wireInstr(24, 20))
  private val wireDataRs1 = WireDefault(UInt(xlen.W), io.gprsR.rdata(0))
  private val wireDataRs2 = WireDefault(UInt(xlen.W), io.gprsR.rdata(1))
  private val wireExcept  = WireDefault(VecInit(Seq.fill(32)(0.B)))
  private val wirePriv    = WireDefault(UInt(2.W), newPriv)
  private val wireIsPriv  = WireDefault(0.B)
  private val wireAmoStat = WireDefault(UInt(1.W), amoStat)
  private val wireRetire  = WireDefault(Bool(), 1.B)
  private val wireBlocked = WireDefault(Bool(), blocked)
  private val wireIsSatp  = WireDefault(0.B)
  private val wireIntr    = if (Debug) WireDefault(0.B) else null

  private val (lessthan, ulessthan, equal) = (wireNum(0).asSInt < wireNum(1).asSInt, wireNum(0) < wireNum(1), wireNum(0) === wireNum(1))
  private val willBranch = MuxLookup(wireInstr(14, 12), equal, Seq(
    "b001".U -> !equal,
    "b100".U -> lessthan,
    "b101".U -> !lessthan,
    "b110".U -> ulessthan,
    "b111".U -> !ulessthan
  ))

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
  io.ifIsPriv       := wireIsPriv
  io.output.priv    := newPriv
  io.output.isPriv  := isPriv
  io.output.isSatp  := isSatp
  io.output.except  := except
  io.output.cause   := cause
  io.output.pc      := pc
  io.gprsR.raddr    := VecInit(10.U, 0.U, 0.U)
  io.csrsR.rcsr     := VecInit(Seq.fill(RegConf.readCsrsPort)(0xFFF.U(12.W)))

  io.csrsR.rcsr(1) := csrsAddr.Mstatus
  io.csrsR.rcsr(2) := csrsAddr.Mie
  io.csrsR.rcsr(3) := csrsAddr.Mideleg
  io.csrsR.rcsr(4) := csrsAddr.Medeleg
  io.csrsR.rcsr(7) := csrsAddr.Mip

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

  wireRd := Mux(decoded(6) === 1.U, wireInstr(11, 7), 0.U)

  io.jmpBch := 0.B; io.jbAddr := 0.U
  private val jbaddr = Mux(decoded(7) === jalr, wireDataRs1(valen - 1, 0), io.input.pc) + wireImm(valen - 1, 0)

  when(decoded(7) === jump || (decoded(7) === branch && willBranch === 1.U)) {
    io.jmpBch := 1.B
    io.jbAddr := jbaddr
  }
  when(decoded(7) === jalr) {
    io.jmpBch := 1.B
    io.jbAddr := jbaddr(valen - 1, 1) ## 0.B
  }
  when(decoded(7) === csr) {
    wireIsWcsr := 1.B
    io.csrsR.rcsr(0) := wireCsr(0)
    wireCsr(0) := wireInstr(31, 20)
    when(wireCsr(0) === csrsAddr.Satp) { wireIsSatp := 1.B }
  }
  when(decoded(7) === inv) { wireExcept(2)  := 1.B } // illegal instruction
  when(decoded(7) === ecall) {
    when(io.currentPriv === "b11".U) { wireExcept(11) := 1.B } // environment call from M-mode
    when(io.currentPriv === "b01".U) { wireExcept( 9) := 1.B } // environment call from S-mode
    when(io.currentPriv === "b00".U) { wireExcept( 8) := 1.B } // environment call from U-mode
  }
  when(decoded(7) === ebreak) { wireExcept(3)  := 1.B } // breakpoint
  when(decoded(7) === fencei) { wireBlocked    := 1.B }
  when(decoded(7) === mret) {
    when(io.currentPriv =/= 3.U) { wireExcept(2) := 1.B } // illegal instruction
    .otherwise {
      io.csrsR.rcsr(0) := csrsAddr.Mepc
      wireIsWcsr := 1.B
      wireCsr(0) := csrsAddr.Mstatus
      wireNum(0) := io.csrsR.rdata(1)
      wirePriv   := io.csrsR.rdata(1).asTypeOf(new MstatusBundle).MPP
      io.jmpBch  := 1.B
      io.jbAddr  := io.csrsR.rdata(0)(valen - 1, 2) ## 0.U(2.W)
    }
  }
  if (extensions.contains('S')) when(decoded(7) === sret) {
    when((io.currentPriv =/= 3.U && io.currentPriv =/= 1.U) || io.csrsR.rdata(1).asTypeOf(new MstatusBundle).TSR) { wireExcept(2) := 1.B } // illegal instruction
    .otherwise {
      io.csrsR.rcsr(0) := csrsAddr.Sepc
      wireIsWcsr := 1.B
      wireCsr(0) := csrsAddr.Mstatus
      wireNum(0) := io.csrsR.rdata(1)
      wirePriv   := io.csrsR.rdata(1).asTypeOf(new MstatusBundle).SPP
      io.jmpBch  := 1.B
      io.jbAddr  := io.csrsR.rdata(0)(valen - 1, 2) ## 0.U(2.W)
    }
  }
  if (extensions.contains('S')) when(decoded(7) === sfence) { wireIsSatp := 1.B }
  when(io.input.except) { wireExcept(io.input.cause) := 1.B }

  private val handleExcept = HandleException()

  if (extensions.contains('A')) when(!handleExcept.fire && decoded(7) === amo && amoStat === idle && wireOp1_2 =/= Operators.lr && wireOp1_2 =/= Operators.sc) {
    wireAmoStat := loading
    wireSpecial := ld
    wireRetire  := 0.B
  }
  if (extensions.contains('A')) when(amoStat === loading) {
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

  io.lastVR.READY := io.nextVR.READY && !io.isWait && !blocked && amoStat === idle && !isSatp

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID     := 1.B
    rd         := wireRd
    isWcsr     := wireIsWcsr
    wcsr       := wireCsr
    num        := wireNum
    op1_2      := wireOp1_2
    op1_3      := wireInstr(14, 12)
    special    := wireSpecial
    instr      := wireInstr
    wireIsPriv := wirePriv =/= io.currentPriv
    newPriv    := wirePriv
    isPriv     := wireIsPriv
    blocked    := wireBlocked
    isSatp     := Mux(io.nextVR.READY && io.nextVR.VALID, 0.B, wireIsSatp)
    if (extensions.contains('A')) amoStat := wireAmoStat
    retire     := wireRetire
    except     := io.input.except
    cause      := io.input.cause
    pc         := io.input.pc
    if (Debug) {
      rcsr := Mux(wireSpecial === csr, wireInstr(31, 20), 0xfff.U)
      intr := wireIntr
    }
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

  if (Debug) {
    io.output.debug.rcsr  := rcsr
    io.output.debug.intr  := intr
    io.output.debug.priv  := newPriv
  }

  private case class HandleException() {
    val fire = WireDefault(0.B)
    private val code = WireDefault(0.U(xlen.W))
    private val intCode = WireDefault("b1111".U(4.W))
    private val isInt = intCode =/= "b1111".U
    private val tmpNewPriv = WireDefault(UInt(2.W), newPriv)
    when(amoStat === idle) {
      if (extensions.contains('U')) Seq(uti, usi, uei).foreach(x => {
        when(mstatus.UIE && io.currentPriv === "b00".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(io.currentPriv === "b11".U && !mideleg(x) && mie(x) && mip(x)) { intCode := x.id.U }
      })
      if (extensions.contains('S')) Seq(sti, ssi, sei).foreach(x => {
        if (extensions.contains('U')) when(io.currentPriv === "b00".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(mstatus.SIE && io.currentPriv === "b01".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(io.currentPriv === "b11".U && !mideleg(x) && mie(x) && mip(x)) { intCode := x.id.U }
      })
      Seq(mti, msi, mei).foreach(x => {
        when(io.currentPriv =/= "b11".U && mie(x) && mip(x)) { intCode := x.id.U }
        when(mstatus.MIE && io.currentPriv === "b11".U && mie(x) && mip(x)) { intCode := x.id.U }
      })
      when(wireExcept(5) | wireExcept(7) | wireExcept(13) | wireExcept(15) | wireExcept(4) | wireExcept(6)) {
        // the mem-access exceptions should be handled first, because they happened "in the past".
        // All other types of exceptions prior to these must not have happened, as the mem-access
        // action can not have been acted if any other exceptions had occurred in previous id level.
        Seq(5,7,13,15,4,6).foreach(i => when(wireExcept(i)) { fire := 1.B; code := i.U })
        when(io.currentPriv <= "b01".U) { tmpNewPriv := Mux(medeleg(code), "b01".U, "b11".U) }
      }.elsewhen(isInt) {
        fire := 1.B
        code := 1.B ## 0.U((xlen - 5).W) ## intCode
        when(io.currentPriv <= "b01".U) { tmpNewPriv := Mux(mideleg(intCode), "b01".U, "b11".U) }
        if (Debug) wireIntr := 1.B
      }.otherwise {
        Seq(24,3,8,9,11,0,2,1,12,25).foreach(i => when(wireExcept(i)) { fire := 1.B; code := i.U }) // 24 for watchpoint, and 25 for breakpoint
        when(io.currentPriv <= "b01".U) { tmpNewPriv := Mux(medeleg(code), "b01".U, "b11".U) } // TODO: user interrupt
      }
      when(io.lastVR.VALID && fire) {
        val mstat   = io.csrsR.rdata(1)(xlen - 1) ## io.currentPriv ## wirePriv ## io.csrsR.rdata(1)(xlen - 6, 0)
        val badAddr = WireDefault(0.B)
        val Xepc    = MuxLookup(wirePriv, csrsAddr.Mepc,   Seq("b01".U -> csrsAddr.Sepc,   "b00".U -> csrsAddr.Uepc  ))
        val Xcause  = MuxLookup(wirePriv, csrsAddr.Mcause, Seq("b01".U -> csrsAddr.Scause, "b00".U -> csrsAddr.Ucause))
        val Xtval   = MuxLookup(wirePriv, csrsAddr.Mtval,  Seq("b01".U -> csrsAddr.Stval,  "b00".U -> csrsAddr.Utval ))
        val Xtvec   = MuxLookup(wirePriv, csrsAddr.Mtvec,  Seq("b01".U -> csrsAddr.Stvec,  "b00".U -> csrsAddr.Utvec ))
        Seq(0,1,12).foreach(i => when(wireExcept(i)) { badAddr := 1.B })
        io.csrsR.rcsr(5) := Xtvec
        io.jmpBch := 1.B
        wirePriv  := tmpNewPriv
        wireSpecial := exception
        wireIsWcsr := 1.B
        wireRd := 0.U
        wireCsr := VecInit(Xepc, Xcause, Xtval, csrsAddr.Mstatus)
        wireNum := VecInit(io.input.pc, code, Mux(badAddr, io.input.pc, 0.U), mstat)
        io.jbAddr := xtvec(valen - 1, 2) ## 0.U(2.W) + Mux(isInt && xtvec(0), code(3, 0) ## 0.U(2.W), 0.U)
      }
    }
  }
}

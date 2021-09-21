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
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
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
  private val pc      = if (Debug) RegInit(0.U(alen.W)) else null
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val clint   = if (Debug) RegInit(0.B) else null
  private val intr    = if (Debug) RegInit(0.B) else null

  private val num = RegInit(VecInit(Seq.fill(4)(0.U(xlen.W))))

  private val decoded = ListLookup(io.input.instr, List(7.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, 0.U, inv), RVInstr().table)

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
  private val wireExcept  = WireDefault(VecInit(Seq.fill(32)(0.B)))
  private val wirePriv    = WireDefault(UInt(2.W), newPriv)
  private val wireIsPriv  = WireDefault(0.B)
  private val wireAmoStat = WireDefault(UInt(1.W), amoStat)
  private val wireRetire  = WireDefault(Bool(), 1.B)
  private val wireBlocked = WireDefault(Bool(), blocked)
  private val wireIsSatp  = WireDefault(0.B)
  private val wireClint   = if (Debug) WireDefault(0.B) else null
  private val wireIntr    = if (Debug) WireDefault(0.B) else null

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
  io.output.priv    := newPriv
  io.output.isPriv  := isPriv
  io.output.isSatp  := isSatp
  io.output.except  := except
  io.output.cause   := cause
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

  wireRd := Mux(decoded(7) === 1.U, wireInstr(11, 7), 0.U)

  private val isClint = Module(new IsCLINT)
  isClint.io.addr_in := wireDataRs1 + wireImm

  io.jmpBch := 0.B; io.jbAddr := 0.U
  private val adder0 = WireDefault(UInt(32.W), io.input.pc)
  private val jbaddr = adder0 + wireImm(alen - 1, 0)

  when(decoded(8) === ld && isClint.io.addr_out =/= 0xFFF.U) {
    io.csrsR.rcsr(0) := isClint.io.addr_out
    wireNum(0) := MuxLookup(decoded(6), 0.U, Seq(
      0.U -> Fill(xlen - 8 , csrr( 7)) ## csrr( 7, 0),
      1.U -> Fill(xlen - 16, csrr(15)) ## csrr(15, 0),
      2.U -> Fill(xlen - 32, csrr(31)) ## csrr(31, 0),
      3.U ->                              csrr       ,
      4.U -> Fill(xlen - 8 ,      0.B) ## csrr( 7, 0),
      5.U -> Fill(xlen - 16,      0.B) ## csrr(15, 0),
      6.U -> Fill(xlen - 32,      0.B) ## csrr(31, 0)
    ))
    wireNum(1) := non; wireNum(2) := non; wireNum(3)  := non
    wireOp1_2  := non; wireOp1_3  := non; wireSpecial := non
    if (Debug) wireClint := 1.B
  }
  when(decoded(8) === st && isClint.io.addr_out =/= 0xFFF.U) {
    io.csrsR.rcsr(0) := isClint.io.addr_out
    wireCsr(0) := isClint.io.addr_out
    wireNum(1) := MuxLookup(decoded(6), 0.U, Seq(
      0.U -> csrr(xlen - 1,  8) ## wireDataRs2( 7, 0),
      1.U -> csrr(xlen - 1, 16) ## wireDataRs2(15, 0),
      2.U -> csrr(xlen - 1, 32) ## wireDataRs2(31, 0),
      3.U ->                       wireDataRs2
    ))
    wireNum(0) := non; wireNum(2) := non; wireNum(3)  := non
    wireOp1_2  := non; wireOp1_3  := 0.U; wireSpecial := csr
    if (Debug) wireClint := 1.B
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
    when(wireCsr(0) === csrsAddr.Satp) { wireIsSatp := 1.B }
  }
  when(decoded(8) === inv) { wireExcept(2)  := 1.B } // illegal instruction
  when(decoded(8) === ecall) {
    when(io.currentPriv === "b11".U) { wireExcept(11) := 1.B } // environment call from M-mode
    when(io.currentPriv === "b01".U) { wireExcept( 9) := 1.B } // environment call from S-mode
    when(io.currentPriv === "b00".U) { wireExcept( 8) := 1.B } // environment call from U-mode
  }
  when(decoded(8) === ebreak) { wireExcept(3)  := 1.B } // breakpoint
  when(decoded(8) === fencei) { wireBlocked    := 1.B }
  when(decoded(8) === mret) {
    when(io.currentPriv =/= 3.U) { wireExcept(2) := 1.B } // illegal instruction
    .otherwise {
      io.csrsR.rcsr(0) := csrsAddr.Mepc
      wireCsr(0) := csrsAddr.Mstatus
      wireNum(0) := io.csrsR.rdata(1)
      wirePriv   := io.csrsR.rdata(1).asTypeOf(new MstatusBundle).MPP
      wireIsPriv := wirePriv =/= io.currentPriv
      io.jmpBch  := 1.B
      io.jbAddr  := io.csrsR.rdata(0)(alen - 1, 2) ## 0.U(2.W)
    }
  }
  if (extensions.contains('S')) when(decoded(8) === sret) {
    when((io.currentPriv =/= 3.U && io.currentPriv =/= 1.U) || io.csrsR.rdata(1).asTypeOf(new MstatusBundle).TSR) { wireExcept(2) := 1.B } // illegal instruction
    .otherwise {
      io.csrsR.rcsr(0) := csrsAddr.Sepc
      wireCsr(0) := csrsAddr.Mstatus
      wireNum(0) := io.csrsR.rdata(1)
      wirePriv   := io.csrsR.rdata(1).asTypeOf(new MstatusBundle).SPP
      wireIsPriv := wirePriv =/= io.currentPriv
      io.jmpBch  := 1.B
      io.jbAddr  := io.csrsR.rdata(0)(alen - 1, 2) ## 0.U(2.W)
    }
  }
  if (extensions.contains('A')) when(decoded(8) === amo && amoStat === idle && wireOp1_2 =/= Operators.lr && wireOp1_2 =/= Operators.sc) {
    wireAmoStat := loading
    wireSpecial := ld
    wireRetire  := 0.B
  }
  when(io.input.except) { wireExcept(io.input.cause) := 1.B }

  new AddException

  io.lastVR.READY := io.nextVR.READY && !io.isWait && !blocked && amoStat === idle && !wireIsSatp && !isSatp

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := 1.B
    rd      := wireRd
    wcsr    := wireCsr
    num     := wireNum
    op1_2   := wireOp1_2
    op1_3   := wireOp1_3
    special := wireSpecial
    instr   := wireInstr
    newPriv := wirePriv
    isPriv  := wireIsPriv
    blocked := wireBlocked
    isSatp  := wireIsSatp
    if (extensions.contains('A')) amoStat := wireAmoStat
    retire  := wireRetire
    except  := io.input.except
    cause   := io.input.cause
    if (Debug) {
      pc := io.input.pc
      rcsr := Mux(wireSpecial === csr, wireInstr(31, 20), 0xfff.U)
      clint := wireClint
      intr := wireIntr
    }
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
    isSatp  := 0.B
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

  if (Debug) {
    io.output.debug.pc    := pc
    io.output.debug.rcsr  := rcsr
    io.output.debug.clint := clint
    io.output.debug.intr  := intr
    io.output.debug.priv  := newPriv
  }

  private class AddException {
    private val fire = WireDefault(0.B)
    private val code = WireDefault(0.U(xlen.W))
    private val intCode = WireDefault("b1111".U(4.W))
    private val isInt = intCode =/= "b1111".U
    private val tmpNewPriv = WireDefault(UInt(2.W), newPriv)
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
      when(!medeleg(code)) { tmpNewPriv := "b11".U }
    }.elsewhen(isInt) {
      fire := 1.B
      code := 1.B ## 0.U((xlen - 5).W) ## intCode
      when(!mideleg(intCode)) { tmpNewPriv := "b11".U }
      if (Debug) wireIntr := 1.B
    }.otherwise {
      Seq(24,3,8,9,11,0,2,1,12,25).foreach(i => when(wireExcept(i)) { fire := 1.B; code := i.U }) // 24 for watchpoint, and 25 for breakpoint
      when(!medeleg(code)) { tmpNewPriv := "b11".U }
    }
    when(io.lastVR.VALID && fire) {
      val mstat  = io.csrsR.rdata(1)(xlen - 1) ## io.currentPriv ## wirePriv ## io.csrsR.rdata(1)(xlen - 6, 0)
      val Xepc   = MuxLookup(wirePriv, csrsAddr.Mepc,   Seq("b01".U -> csrsAddr.Sepc,   "b00".U -> csrsAddr.Uepc  ))
      val Xcause = MuxLookup(wirePriv, csrsAddr.Mcause, Seq("b01".U -> csrsAddr.Scause, "b00".U -> csrsAddr.Ucause))
      val Xtval  = MuxLookup(wirePriv, csrsAddr.Mtval,  Seq("b01".U -> csrsAddr.Stval,  "b00".U -> csrsAddr.Utval ))
      val Xtvec  = MuxLookup(wirePriv, csrsAddr.Mtvec,  Seq("b01".U -> csrsAddr.Stvec,  "b00".U -> csrsAddr.Utvec ))
      io.csrsR.rcsr(5) := Xtvec
      io.jmpBch := 1.B
      wirePriv  := tmpNewPriv
      wireIsPriv := wirePriv =/= io.currentPriv
      wireSpecial := exception
      wireRd := 0.U
      wireCsr := VecInit(Xepc, Xcause, Xtval, csrsAddr.Mstatus)
      wireNum := VecInit(io.input.pc, code, io.input.instr, mstat)
      io.jbAddr := xtvec(alen - 1, 2) ## 0.U(2.W) + Mux(isInt && xtvec(0), code(3, 0) ## 0.U(2.W), 0.U)
    }
  }
}

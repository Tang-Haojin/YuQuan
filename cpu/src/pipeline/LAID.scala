package cpu.pipeline

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.component._
import ExecSpecials._
import cpu.tools._
import cpu._
import cpu.privileged._

// instruction decoding module
class LAID(implicit p: Parameters) extends AbstractID with cpu.privileged.LACSRsAddr {
  import LAInstrTypes._

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
  private val retire  = RegInit(0.B)
  private val isSatp  = RegInit(0.B)
  private val except  = RegInit(0.B)
  private val cause   = RegInit(0.U(4.W))
  private val jmpBch  = RegInit(0.B)
  private val jbAddr  = RegInit(0.U(valen.W))
  private val jbPend  = RegInit(0.B)
  private val isIdle  = RegInit(0.B)
  private val isTlbrw = RegInit(0.B)
  private val num = RegInit(VecInit(Seq.fill(4)(0.U(xlen.W))))

  private val stableCounter = RegInit(0.U(64.W))
  stableCounter := stableCounter + 1.U
  private val counter = RegInit(0.U(64.W))

  private val decoded = LAInstrDecoder(io.input.instr)

  private val wireInstr   = io.input.instr
  private val wireSpecial = WireDefault(UInt(5.W), decoded(7))
  private val wireType    = WireDefault(7.U(3.W))
  private val wireRd      = Wire(UInt(5.W))
  private val wireIsWcsr  = WireDefault(0.B)
  private val wireCsr     = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val wireOp1_2   = WireDefault(UInt(Operators.quantity.W), decoded(5))
  private val wireOp1_3   = WireDefault(io.input.instr(25) ## io.input.instr(23, 22))
  private val wireNum     = WireDefault(VecInit(Seq.fill(4)(0.U(xlen.W))))
  private val wireImm     = WireDefault(0.U(xlen.W))
  private val wireRs1     = WireDefault(UInt(5.W), io.input.rs(0))
  private val wireRs2     = WireDefault(UInt(5.W), io.input.rs(1))
  private val wireExcept  = WireDefault(0.B)
  private val wireEcode   = WireDefault(0.U(6.W))
  private val wireEsub    = WireDefault(0.U(9.W))
  private val wirePriv    = WireDefault(UInt(2.W), io.currentPriv)
  private val wireIsPriv  = WireDefault(0.B)
  private val wireRetire  = WireDefault(Bool(), 1.B)
  private val wireBlocked = WireDefault(Bool(), blocked)
  private val wireIsSatp  = WireDefault(0.B)
  private val wireIsTlbrw = WireDefault(0.B)

  private val lessthan  = io.gprsR.rdata(1).asSInt <   io.gprsR.rdata(0).asSInt
  private val ulessthan = io.gprsR.rdata(1)        <   io.gprsR.rdata(0)
  private val equal     = io.gprsR.rdata(1)        === io.gprsR.rdata(0)

  private val instRs = Seq.tabulate(3)(i => io.input.instr(i * 5 + 4, i * 5))
  private val Seq(instRd, instRj, instRk) = instRs
  private val Seq(readRd, readRj, readRk) = io.gprsR.rdata

  private def hasNumType(numType: UInt): Bool = VecInit(decoded.slice(1, 5).map(_ === numType)).asUInt.orR
  private val hasRsType = Seq(LANumTypes.rd, LANumTypes.rj, LANumTypes.rk).map(hasNumType(_))

  io.gprsR.raddr := (instRs zip hasRsType).map {
    case (rs, has) => VecInit(rs.asBools.map(_ & has)).asUInt
  }

  private val immMap = Seq(
    i16 -> Fill(xlen - 18, io.input.instr(25)) ## io.input.instr(25, 10) ## 0.U(2.W),
    i26 -> Fill(xlen - 28, io.input.instr(9)) ## io.input.instr(9, 0) ## io.input.instr(25, 10) ## 0.U(2.W),
    i12 -> Fill(xlen - 12, !io.input.instr(25, 24).andR && io.input.instr(21)) ## io.input.instr(21, 10),
    i14 -> Fill(xlen - 16, io.input.instr(23)) ## io.input.instr(23, 10) ## 0.U(2.W),
    i20 -> io.input.instr(24, 5) ## 0.U(12.W),
    r2  -> 0.U(32.W),
    r3  -> 0.U(32.W)
  )

  wireImm := Mux1H(immMap.map(x => (decoded.head === x._1, x._2)))
  wireRd := Fill(5, decoded(6)(0)) & Mux(io.input.instr(31, 27) === "b01010".U, 0.U(4.W) ## io.input.instr(26), instRd)

  for (i <- wireNum.indices) wireNum(i) := Mux1H(decoded(i + 1), Seq(
    /* non  */ 0.U,
    /* rd   */ readRd,
    /* rj   */ readRj,
    /* rk   */ readRk,
    /* imm  */ wireImm,
    /* four */ 4.U,
    /* pc   */ io.input.pc,
    /* csr  */ io.csrsR.rdata(0)
  ))

  private val wireJmpBch = WireDefault(
    io.input.instr(31, 30) === "b01".U &&
    MuxLookup(io.input.instr(29, 26), 1.B, Seq(
      "b0110".U -> equal,
      "b0111".U -> !equal,
      "b1000".U -> lessthan,
      "b1001".U -> !lessthan,
      "b1010".U -> ulessthan,
      "b1011".U -> !ulessthan
    ))
  )

  private val wireJbAddr = WireDefault(Mux(io.input.instr(29, 26) === "b0011".U, readRj, io.input.pc) + wireImm)

  io.jmpBch := jmpBch; io.jbAddr := jbAddr
  io.lastVR.READY := io.nextVR.READY && !io.isWait && !blocked && !isIdle
  io.nextVR.VALID   := NVALID
  io.output.rd      := rd
  io.output.isWcsr  := isWcsr
  io.output.wcsr    := wcsr
  io.output.num     := num
  io.output.op1_2   := op1_2
  io.output.op1_3   := op1_3
  io.output.special := special
  io.output.retire  := retire
  io.isAmo          := 0.B
  io.output.priv    := newPriv
  io.output.isPriv  := isPriv
  io.output.isSatp  := isSatp
  io.output.except  := except
  io.output.cause   := cause
  io.output.pc      := pc
  io.csrsR.rcsr     := VecInit(Seq.fill(RegConf.readCsrsPort)(0xFFF.U(12.W)))
  io.output.isTlbrw.get := isTlbrw
  if (io.output.diff.isDefined) {
    io.output.diff.get.instr := instr
    io.output.diff.get.allExcept := special === exception
    io.output.diff.get.eret := special === mret
    io.output.diff.get.is_CNTinst := special === rdcnt
    io.output.diff.get.timer_64_value := counter
  }

  io.csrsR.rcsr(0) := io.input.instr(21, 10)
  io.csrsR.rcsr(1) := CRMD
  io.csrsR.rcsr(2) := PRMD
  io.csrsR.rcsr(3) := ECFG
  io.csrsR.rcsr(4) := ESTAT
  io.csrsR.rcsr(5) := ERA
  io.csrsR.rcsr(6) := EENTRY
  io.csrsR.rcsr(7) := LLBCTL
  io.csrsR.rcsr(8) := TLBRENTRY
  private val crmd      = io.csrsR.rdata(1).asTypeOf(new CRMDBundle)
  private val prmd      = io.csrsR.rdata(2).asTypeOf(new PRMDBundle)
  private val ecfg      = io.csrsR.rdata(3).asTypeOf(new ECFGBundle)
  private val estat     = io.csrsR.rdata(4).asTypeOf(new ESTATBundle)
  private val era       = io.csrsR.rdata(5)
  private val eentry    = io.csrsR.rdata(6)
  private val llbctl    = io.csrsR.rdata(7).asTypeOf(new LLBCTLBundle)
  private val tlbrentry = io.csrsR.rdata(8)

  when(decoded(7) === zicsr) {
    wireIsWcsr := io.input.instr(9, 5) =/= 0.U
    wireCsr(0) := io.input.instr(21, 10)
    wireOp1_3  := io.input.instr(9, 5)
    wireIsSatp := 1.B
  }
  when(decoded(7) === tlbrw) {
    wireIsSatp  := 1.B
    wireIsTlbrw := 1.B
    wireOp1_3   := io.input.instr(12) ## io.input.instr(10)
    wireIsWcsr  := wireOp1_3(1) === 0.B
  }
  when(decoded(7) === invtlb) {
    wireIsSatp := 1.B
    wireOp1_3  := io.input.instr(2, 0)
    when(io.input.instr(4, 0) > 0x6.U) { // INV
      wireExcept := 1.B
      wireEcode  := 0xD.U
      wireEsub   := 0.U
    }
  }
  when(decoded(7) === rdcnt) {
    io.csrsR.rcsr(0) := TID
    when(io.input.instr(10)) { wireNum(0) := stableCounter(63, 32) }
    .elsewhen(io.input.instr(9, 5).orR) {
      wireRd := instRj
      wireNum(0) := io.csrsR.rdata(0)
    }
    .otherwise { wireNum(0) := stableCounter(31, 0) }
  }
  when(decoded(7) === ecall) {
    wireExcept := 1.B
    wireEcode  := 0xB.U
    wireEsub   := 0.U
  }
  when(decoded(7) === ebreak) {
    wireExcept := 1.B
    wireEcode  := 0xC.U
    wireEsub   := 0.U
  }
  when(decoded(7) === inv) {
    wireExcept := 1.B
    wireEcode  := 0xD.U
    wireEsub   := 0.U
  }
  when(decoded(7) === mret) {
    wireIsWcsr := 1.B
    wireCsr(0) := CRMD
    wireCsr(2) := LLBCTL
    wireNum(0) := crmd.replace(
      _.DA := Mux(estat.Ecode === 0x3F.U, 0.B, crmd.DA),
      _.PG := Mux(estat.Ecode === 0x3F.U, 1.B, crmd.PG)
    ).asUInt
    wireNum(1) := prmd.asUInt
    wireNum(2) := llbctl.asUInt
    wireIsPriv := crmd.PLV =/= prmd.PPLV
    wireJmpBch := 1.B
    wireJbAddr := era
  }
  when(decoded(7) === amo) {
    wireIsWcsr := 1.B
    wireCsr(0) := LLBCTL
    wireNum(1) := llbctl.asUInt
    wireOp1_3 := "b10".U
  }
  when(io.input.except) {
    wireExcept := 1.B
    wireEcode  := MuxLookup(io.input.cause, io.input.cause, Seq(0x5.U -> 0x8.U, 0x6.U -> 0x3F.U))
    wireEsub   := (io.input.cause === 0x5.U).asUInt
  }
  when(crmd.IE && (ecfg.LIE.asUInt & estat.IS.asUInt).orR) {
    wireExcept := 1.B
    wireEcode  := 0x0.U
    wireEsub   := 0x0.U
    isIdle := 0.B
  }

  private val newCrmd = WireDefault(crmd)
  newCrmd.PLV := 0.U
  newCrmd.IE  := 0.U
  when(io.input.cause === 0x6.U) {
    newCrmd.DA := 1.B
    newCrmd.PG := 0.B
  }
  private val newPrmd = WireDefault(prmd)
  newPrmd.PPLV := crmd.PLV
  newPrmd.PIE  := crmd.IE
  private val newEstat = WireDefault(estat)
  newEstat.Ecode    := wireEcode
  newEstat.EsubCode := wireEsub
  private val WBADV = VecInit((0x1 to 0x9).map(_.U)).contains(io.input.cause)
  private val WTLBEHI = VecInit(Seq(0x1, 0x2, 0x3, 0x4, 0x6, 0x7).map(_.U)).contains(io.input.cause)
  when(io.lastVR.VALID && wireExcept) {
    wireJmpBch := 1.B
    wireJbAddr := Mux(wireEcode === 0x3F.U, tlbrentry, eentry)
    wireSpecial := exception
    wireIsPriv := crmd.PLV =/= 0.U
    wireCsr := Seq(CRMD, PRMD, ESTAT, ERA, Mux(WBADV, BADV, 0xFFF.U), Mux(WTLBEHI, TLBEHI, 0xFFF.U))
    wireNum := Seq(newCrmd.asUInt, newPrmd.asUInt, newEstat.asUInt, io.input.pc)
    wireIsSatp := 0.B; wireBlocked := 0.B
  }

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    when(!jbPend || jbAddr === io.input.pc || io.input.memExcept) {
      NVALID     := 1.B
      rd         := wireRd
      isWcsr     := wireIsWcsr
      wcsr       := wireCsr
      num        := wireNum
      op1_2      := wireOp1_2
      op1_3      := wireOp1_3
      special    := wireSpecial
      instr      := wireInstr
      newPriv    := wirePriv
      isPriv     := wireIsPriv
      blocked    := wireBlocked
      isSatp     := wireIsSatp
      retire     := wireRetire
      except     := io.input.except
      cause      := io.input.cause
      pc         := io.input.pc
      jbPend     := 0.B
      jbAddr     := wireJbAddr
      isIdle     := decoded(7) === exidle
      counter    := stableCounter
      isTlbrw    := wireIsTlbrw
      when(wireJmpBch && wireJbAddr =/= io.input.pc + 4.U) { jmpBch := 1.B; jbPend := 1.B }
    }.otherwise { NVALID := 0.B }
  }.otherwise {
    when(io.isWait && io.nextVR.READY) {
      NVALID  := 0.B
      rd      := 0.U
      isWcsr  := 0.B
      wcsr    := VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W)))
      num     := VecInit(Seq.fill(4)(0.U))
      op1_2   := 0.U
      op1_3   := 0.U
      special := 0.U
      isTlbrw := 0.B
    }
    when(io.nextVR.READY && io.nextVR.VALID) {
      NVALID  := 0.B
      blocked := 0.B
      isSatp  := 0.B
    }
  }

  when(jmpBch) { jmpBch := 0.B }
  when(io.input.memExcept) { isIdle := 0.B }
}

package cpu.pipeline

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import ExecSpecials._
import cpu.component._
import cpu.tools._
import cpu.privileged._

class EX(implicit p: Parameters) extends YQModule {
  val io = IO(new EXIO)

  io.invIch.bits := DontCare; io.wbDch.bits := DontCare

  private val idle::storing::Nil = Enum(2)
  private val alu        = Module(new ALU)
  private val op         = RegInit(0.U(Operators.quantity.W))
  private val wireOp     = WireDefault(UInt(Operators.quantity.W), op)
  private val isWord     = RegInit(0.B)
  private val wireIsWord = WireDefault(Bool(), isWord)
  alu.io.input.bits.a    := io.input.num(0).asSInt
  alu.io.input.bits.b    := io.input.num(1).asSInt
  alu.io.input.bits.op   := wireOp
  alu.io.input.bits.word := wireIsWord
  alu.io.input.bits.sign := ((io.input.special =/= mu) && (if (!isLxb) io.input.special =/= msu else 1.B)) ## (io.input.special =/= mu)
  alu.io.input.valid     := io.lastVR.VALID
  alu.io.output.ready    := io.nextVR.READY

  private val NVALID = RegInit(0.B); io.nextVR.VALID := NVALID

  private val invalidateICache = RegInit(0.B)
  private val writebackDCache  = RegInit(0.B)

  private val rd      = RegInit(0.U(5.W))
  private val pc      = RegInit(0.U(valen.W))
  private val data    = RegInit(0.U(xlen.W))
  private val isWcsr  = RegInit(0.B)
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W))))
  private val csrData = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))))
  private val isMem   = RegInit(0.B)
  private val isLd    = RegInit(0.B)
  private val addr    = RegInit(0.U(valen.W))
  private val mask    = RegInit(0.U(3.W))
  private val retire  = RegInit(0.B)
  private val lraddr  = RegInit(0.U(valen.W))
  private val lrvalid = RegInit(0.B)
  private val scState = RegInit(UInt(1.W), idle)
  private val tmpRd   = RegInit(0.U(5.W))
  private val priv    = RegInit("b11".U(2.W))
  private val isPriv  = RegInit(0.B)
  private val isSatp  = RegInit(0.B)
  private val except  = RegInit(0.B)
  private val memExpt = RegInit(0.B)
  private val cause   = RegInit(0.U(4.W))
  private val instr   = RegInit(0.U(32.W))
  private val allExpt = RegInit(0.B)
  private val isEret  = RegInit(0.B)
  private val isRdcnt = RegInit(0.B)
  private val counter = RegInit(0.U(64.W))
  private val isTlbrw = RegInit(0.B)
  private val fshTLB  = RegInit(0.B)
  private val exit    = if (Debug) RegInit(0.U(3.W)) else null
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val intr    = if (Debug) RegInit(0.B) else null
  private val rvc     = if (Debug) RegInit(0.B) else null

  private val wireRd      = WireDefault(UInt(5.W), io.input.rd)
  private val wireData    = WireDefault(UInt(xlen.W), alu.io.output.bits.asUInt)
  private val wireCsrData = WireDefault(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))))
  private val wireIsWcsr  = WireDefault(Bool(), io.input.isWcsr)
  private val wireIsMem   = WireDefault(Bool(), io.input.special === ld || io.input.special === st || (if (isLxb) 0.B else io.input.special === sfence))
  private val wireIsLd    = WireDefault(Bool(), io.input.special === ld || (if (isLxb) io.input.special === cacop && io.input.op1_3 === 2.U else 0.B))
  private val wireAddr    = WireDefault(UInt(valen.W), io.input.num(2)(valen - 1, 0) + io.input.num(3)(valen - 1, 0))
  private val wireMask    = WireDefault(UInt(3.W), io.input.op1_3)
  private val wireRetire  = WireDefault(Bool(), io.input.retire)
  private val wireLraddr  = WireDefault(UInt(valen.W), lraddr)
  private val wireLrvalid = WireDefault(Bool(), lrvalid)
  private val wireScState = WireDefault(UInt(1.W), scState)
  private val wireTmpRd   = WireDefault(UInt(5.W), tmpRd)
  private val wireIsTlbrw = WireDefault(Bool(), io.input.isTlbrw.getOrElse(0.B))
  private val wireExit    = if (Debug) WireDefault(UInt(3.W), ExitReasons.non) else null

  wireCsrData zip io.input.num foreach(x => x._1 := x._2)

  io.output.rd      := rd
  io.output.pc      := pc
  io.output.data    := data
  io.output.isWcsr  := isWcsr
  io.output.wcsr    := wcsr
  io.output.csrData := csrData
  io.output.isMem   := isMem
  io.output.isLd    := isLd
  io.output.addr    := addr
  io.output.mask    := mask
  io.output.retire  := retire
  io.output.priv    := priv
  io.output.isPriv  := isPriv
  io.output.isSatp  := isSatp
  io.output.except  := except
  io.output.memExpt := memExpt
  io.output.cause   := cause
  io.output.isTlbrw := isTlbrw
  io.output.fshTLB  := fshTLB

  io.invIch.valid   := invalidateICache
  io.wbDch.valid    := writebackDCache
  when(io.invIch.fire) { invalidateICache := 0.B }
  when(io.wbDch.fire)  { writebackDCache  := 0.B }

  when(io.input.special === zicsr) {
    if (isLxb) {
      wireCsrData(0) := Mux(
        io.input.op1_3 === 1.U,
        io.input.num(1),
        io.input.num(1) & io.input.num(2) | io.input.num(0) & ~io.input.num(2)
      )
    } else {
      case class csrsAddr()(implicit val p: Parameters) extends cpu.CPUParams with cpu.privileged.CSRsAddr
      val oldValue = io.input.num(0).asTypeOf(new MipBundle)
      val newValue = WireDefault(new MipBundle, oldValue)
      if (ext('S')) newValue.SEIP := Mux(io.input.wcsr(0) === csrsAddr().Mip, io.seip, oldValue.SEIP)
      if (ext('U')) newValue.UEIP := Mux(io.input.wcsr(0) === csrsAddr().Mip || io.input.wcsr(0) === csrsAddr().Sip, io.ueip, oldValue.UEIP)
      wireCsrData(0) := MuxLookup(io.input.op1_3(1, 0), 0.U, Seq(
        1.U -> io.input.num(1),
        2.U -> (newValue | io.input.num(1)),
        3.U -> (newValue & ~io.input.num(1))
      ))
    }
  }
  if (!isZmb) when(io.input.special === mret) {
    if (isLxb) {
      val newCrmd = WireDefault(io.input.num(0).asTypeOf(new CRMDBundle))
      val prmd = io.input.num(1).asTypeOf(new PRMDBundle)
      val llbctl = io.input.num(2).asTypeOf(new LLBCTLBundle)
      val newLlbctl = WireDefault(llbctl)
      newCrmd.PLV := prmd.PPLV
      newCrmd.IE  := prmd.PIE
      newLlbctl.ROLLB := llbctl.KLO
      newLlbctl.KLO   := 0.B
      wireCsrData(0) := newCrmd.asUInt
      wireCsrData(2) := newLlbctl.asUInt
    } else {
      wireCsrData(0) := Cat(
        io.input.num(0)(xlen - 1, 13),
        0.U(2.W),
        io.input.num(0)(10, 8),
        1.B,
        io.input.num(0)(6, 4),
        io.input.num(0)(7), // MPIE
        io.input.num(0)(2, 0)
      )
    }
  }
  if (ext('S')) when(io.input.special === sret) {
    val oldMstatus = io.input.num(0).asTypeOf(new MstatusBundle)
    val newMstatus = WireDefault(new MstatusBundle, oldMstatus)
    newMstatus.SPP  := 0.B
    newMstatus.SIE  := oldMstatus.SPIE
    newMstatus.SPIE := 1.B
    wireCsrData(0)  := newMstatus.asUInt
  }
  if (!isZmb) when(io.input.special === exception) {
    if (!isLxb) {
      wireCsrData := io.input.num
      val currentPriv = io.input.num(3)(xlen - 2, xlen - 3)
      val newPriv     = io.input.num(3)(xlen - 4, xlen - 5)
      val oldMstatus  = io.input.num(3).asTypeOf(new MstatusBundle)
      val newMstatus  = WireDefault(new MstatusBundle, oldMstatus)
      newMstatus.WPRI_0 := 0.U
      when(newPriv === "b11".U) {
        newMstatus.MPP  := currentPriv
        newMstatus.MPIE := oldMstatus.MIE
        newMstatus.MIE  := 0.B
      }
      if (ext('S')) when(newPriv === "b01".U) {
        newMstatus.SPP  := currentPriv
        newMstatus.SPIE := oldMstatus.SIE
        newMstatus.SIE  := 0.B
      }
      if (ext('U')) when(newPriv === "b00".U) {
        newMstatus.UPIE := oldMstatus.UIE
        newMstatus.UIE  := 0.B
      }
      wireCsrData(3) := newMstatus.asUInt
    } else {
      wireCsrData := io.input.num :+ io.input.num(3) :+ io.input.num(3)
    }
    wireRd := 0.U
    wireIsWcsr := 1.B
  }
  if (ext('A') || isLxb) when(io.input.special === amo) {
    when(io.input.op1_2 === Operators.lr) {
      wireIsMem   := 1.B
      wireIsLd    := 1.B
      if (isLxb) {
        wireCsrData(0) := io.input.num(1) | 1.U(xlen.W)
      } else {
        wireLraddr  := wireAddr
        wireLrvalid := 1.B
      }
    }
    when(io.input.op1_2 === Operators.sc) {
      if (!isLxb) wireLrvalid := 0.B
      (if (isLxb) when(!io.input.num(1)(0))             { wireData := 0.U }
       else       when(wireAddr =/= lraddr || !lrvalid) { wireData := 1.U })
      .otherwise {
        wireScState := storing
        wireRetire  := 0.B
        wireRd      := 0.U
        wireIsMem   := 1.B
        wireTmpRd   := io.input.rd
      }
      if (isLxb) wireCsrData(0) := io.input.num(1)(xlen - 1, 1) ## 0.B
    }
  }

  if (Debug) switch(io.input.special) {
    is(trap) {             wireExit := ExitReasons.trap }
    is(inv)  { if (!isZmb) wireExit := ExitReasons.inv  }
  }

  io.lastVR.READY := io.nextVR.READY && alu.io.input.ready && !invalidateICache && !writebackDCache && scState === idle

  when(alu.io.output.fire) {
    data   := wireData
    NVALID := 1.B
  }

  when(io.lastVR.VALID && io.lastVR.READY) { // let's start working
    NVALID  := alu.io.output.fire
    pc      := io.input.pc
    rd      := wireRd
    data    := wireData
    isWcsr  := wireIsWcsr
    wcsr    := io.input.wcsr
    csrData := wireCsrData
    isMem   := wireIsMem
    isLd    := wireIsLd
    addr    := wireAddr
    mask    := wireMask
    retire  := wireRetire
    lraddr  := wireLraddr
    lrvalid := wireLrvalid
    scState := wireScState
    tmpRd   := wireTmpRd
    isTlbrw := wireIsTlbrw
    priv    := io.input.priv
    isPriv  := io.input.isPriv
    isSatp  := io.input.isSatp
    except  := io.input.except
    memExpt := io.input.memExpt
    cause   := io.input.cause
    fshTLB  := io.input.special === sfence

    op      := wireOp
    isWord  := wireIsWord

    if (!isZmb) invalidateICache := io.input.special === fencei || (if (isLxb) io.input.special === cacop else 0.B)
    if (!isZmb) writebackDCache  := io.input.special === fencei || (if (isLxb) io.input.special === cacop else 0.B)

    wireOp     := io.input.op1_2
    wireIsWord := (io.input.special === word)
    if (Debug) {
      exit := wireExit
      rcsr := io.input.debug.rcsr
      intr := io.input.debug.intr
      rvc  := io.input.debug.rvc
    }
    if (io.input.diff.isDefined) {
      instr := io.input.diff.get.instr
      allExpt := io.input.diff.get.allExcept
      isEret := io.input.diff.get.eret
      isRdcnt := io.input.diff.get.is_CNTinst
      counter := io.input.diff.get.timer_64_value
    }
  }.elsewhen(io.nextVR.READY && io.nextVR.VALID) {
    NVALID := 0.B
    isSatp := 0.B
    isPriv := 0.B
  }

  if (ext('A') || isLxb) when(scState === storing) {
    scState := idle
    rd      := tmpRd
    data    := isLxb.B.asUInt
    isMem   := 0.B
    retire  := 1.B
    NVALID  := 1.B
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.rcsr := rcsr
    io.output.debug.intr := intr
    io.output.debug.rvc  := rvc
  }

  if (io.output.diff.isDefined) {
    io.output.diff.get.instr := instr
    io.output.diff.get.allExcept := allExpt
    io.output.diff.get.eret := isEret
    io.output.diff.get.is_CNTinst := isRdcnt
    io.output.diff.get.timer_64_value := counter
  }
}

package cpu.privileged

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.CPUParams
import cpu.tools._
import cpu.tools.difftest._

trait LACSRsAddr extends CPUParams {
  val CRMD = 0x0.U
  val PRMD = 0x1.U
  val EUEN = 0x2.U
  val ECFG = 0x4.U
  val ESTAT = 0x5.U
  val ERA = 0x6.U
  val BADV = 0x7.U
  val EENTRY = 0xC.U
  val TLBIDX = 0x10.U
  val TLBEHI = 0x11.U
  val TLBELO0 = 0x12.U
  val TLBELO1 = 0x13.U
  val ASID = 0x18.U
  val PGDL = 0x19.U
  val PGDH = 0x1A.U
  val PGD = 0x1B.U
  val CPUID = 0x20.U
  val SAVE = VecInit(Seq.tabulate(4)(index => (0x30 + index).U))
  val TID = 0x40.U
  val TCFG = 0x41.U
  val TVAL = 0x42.U
  val TICLR = 0x44.U
  val LLBCTL = 0x60.U
  val TLBRENTRY = 0x88.U
  val CTAG = 0x98.U
  val DMW = VecInit(Seq.tabulate(2)(index => (0x180 + index).U))
}

class LACSRs(implicit p: Parameters) extends AbstractCSRs with LACSRsAddr {
  val difftestIO = IO(Output(new DifftestCSRRegStateIO))
  private val crmd = RegInit((new CRMDBundle).Lit(
    _.PLV  -> 0.U(2.W),
    _.IE   -> 0.B,
    _.DA   -> 1.B,
    _.PG   -> 0.B,
    _.DATF -> 0.U(2.W),
    _.DATM -> 0.U(2.W)
  ))
  io.currentPriv := ~crmd.PLV
  private val prmd   = Reg(new PRMDBundle)
  private val ecfg   = RegInit(0.U.asTypeOf(new ECFGBundle))
  private val estat  = RegInit(0.U.asTypeOf(new ESTATBundle))
  private val era    = Reg(UInt(32.W))
  private val badv   = Reg(UInt(32.W))
  private val eentry = Reg(UInt((32 - 6).W))
  private val save   = Reg(Vec(4, UInt(32.W)))
  private val llbctl = RegInit(0.U.asTypeOf(new LLBCTLBundle))

  if (useDifftest) {
    difftestIO.connect(
      _        := 0.U.asTypeOf(difftestIO),
      _.crmd   := crmd.asUInt,
      _.prmd   := prmd.asUInt,
      _.ecfg   := ecfg.asUInt,
      _.estat  := estat.asUInt,
      _.euen   := 0.U,
      _.era    := era,
      _.badv   := badv,
      _.eentry := eentry ## 0.U(6.W),
      _.coreid := 0.U,
      _.save0  := save(0),
      _.save1  := save(1),
      _.save2  := save(2),
      _.save3  := save(3),
      _.llbctl := llbctl.asUInt
    )
  } else difftestIO := DontCare

  for (i <- 0 until RegConf.writeCsrsPort) {
    when(io.csrsW.wen(i)) {
      when(io.csrsW.wcsr(i) === CRMD)   { crmd   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === PRMD)   { prmd   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === EUEN)   {}
      when(io.csrsW.wcsr(i) === ECFG)   { ecfg   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === ESTAT)  { estat  := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === ERA)    { era    := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === BADV)   { badv   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === EENTRY) { eentry := io.csrsW.wdata(i)(31, 6) }
      when(io.csrsW.wcsr(i) === CPUID)  {}
      SAVE zip save foreach { case (addr, save) =>
        when(io.csrsW.wcsr(i) === addr) { save   := io.csrsW.wdata(i) }
      }
      when(io.csrsW.wcsr(i) === LLBCTL) { llbctl := io.csrsW.wdata(i) }
    }
  }

  for (i <- 0 until RegConf.readCsrsPort) {
    io.csrsR.rdata(i) := 0.U
    when(io.csrsR.rcsr(i) === CRMD)   { io.csrsR.rdata(i) := crmd.asUInt }
    when(io.csrsR.rcsr(i) === PRMD)   { io.csrsR.rdata(i) := prmd.asUInt }
    when(io.csrsR.rcsr(i) === EUEN)   { io.csrsR.rdata(i) := 0.U }
    when(io.csrsR.rcsr(i) === ECFG)   { io.csrsR.rdata(i) := ecfg.asUInt }
    when(io.csrsR.rcsr(i) === ESTAT)  { io.csrsR.rdata(i) := estat.asUInt }
    when(io.csrsR.rcsr(i) === ERA)    { io.csrsR.rdata(i) := era }
    when(io.csrsR.rcsr(i) === BADV)   { io.csrsR.rdata(i) := badv }
    when(io.csrsR.rcsr(i) === EENTRY) { io.csrsR.rdata(i) := eentry ## 0.U(6.W) }
    when(io.csrsR.rcsr(i) === CPUID)  { io.csrsR.rdata(i) := 0.U }
    SAVE zip save foreach { case (addr, save) =>
      when(io.csrsR.rcsr(i) === addr) { io.csrsR.rdata(i) := save }
    }
    when(io.csrsR.rcsr(i) === LLBCTL) { io.csrsR.rdata(i) := llbctl.asUInt }
  }

  io.bareSEIP := DontCare; io.bareUEIP := DontCare
  io.satp := DontCare
  io.sum  := DontCare
  io.mprv := DontCare
  io.mpp  := DontCare
}

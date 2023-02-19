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

class LACSR2MMUBundle(implicit p: Parameters) extends YQBundle {
  val crmd   = new CRMDBundle
  val dmw    = Vec(2, new DMWBundle)
  val asid   = new ASIDBundle
  val tlbehi = UInt(32.W)
  val tlbelo = Vec(2, new TLBELOBundle)
  val tlbidx = new TLBIDXBundle
  val estat  = new ESTATBundle
}

class LAMMU2CSRBundle(implicit p: Parameters) extends YQBundle {
  val valid  = Bool()
  val asid   = new ASIDBundle
  val tlbehi = UInt(32.W)
  val tlbelo = Vec(2, new TLBELOBundle)
  val tlbidx = new TLBIDXBundle
}

class LAIFMMUBundle(sets: Int)(implicit p: Parameters) extends YQBundle {
  val select = Output(Vec(sets, Bool()))
  val addr   = Output(Vec(sets, UInt(valen.W)))
}

class LACSRs(implicit p: Parameters) extends AbstractCSRs with LACSRsAddr {
  val difftestIO = IO(Output(new DifftestCSRRegStateIO))
  val laIO = IO(Input(new LAMMU2CSRBundle))
  private val laIOWrite = RegNext(laIO, 0.U.asTypeOf(laIO)) // make sure it writeback at WB stage
  private val crmd = RegInit((new CRMDBundle).Lit(
    _.PLV  -> 0.U(2.W),
    _.IE   -> 0.B,
    _.DA   -> 1.B,
    _.PG   -> 0.B,
    _.DATF -> 0.U(2.W),
    _.DATM -> 0.U(2.W)
  ))
  io.currentPriv := ~crmd.PLV
  private val cnten     = RegInit(0.B)
  private val prmd      = Reg(new PRMDBundle)
  private val ecfg      = RegInit(0.U.asTypeOf(new ECFGBundle))
  private val estat     = RegInit(0.U.asTypeOf(new ESTATBundle))
  private val era       = Reg(UInt(32.W))
  private val badv      = Reg(UInt(32.W))
  private val eentry    = Reg(UInt((32 - 6).W))
  private val save      = Reg(Vec(4, UInt(32.W)))
  private val llbctl    = RegInit(0.U.asTypeOf(new LLBCTLBundle))
  private val dmw       = RegInit(VecInit(Seq.fill(2)(0.U.asTypeOf(new DMWBundle))))
  private val tid       = Reg(UInt(32.W))
  private val tval      = RegInit(0.U(32.W)) // reset is not necessary
  private val tcfg      = RegInit(0.U.asTypeOf(new TCFGBundle))
  private val asid      = RegInit((new ASIDBundle).Lit(_.ASID -> 0.U, _.RES -> 0.U, _.ASIDBITS -> 10.U))
  private val tlbidx    = RegInit(0.U.asTypeOf(new TLBIDXBundle))
  private val tlbehi    = Reg(UInt((32 - 13).W))
  private val tlbelo0   = RegInit(0.U.asTypeOf(new TLBELOBundle))
  private val tlbelo1   = RegInit(0.U.asTypeOf(new TLBELOBundle))
  private val pgdl      = Reg(UInt((32 - 12).W))
  private val pgdh      = Reg(UInt((32 - 12).W))
  private val tlbrentry = Reg(UInt((32 - 6).W))

  if (useDifftest) {
    difftestIO.connect(
      _           := 0.U.asTypeOf(difftestIO),
      _.crmd      := crmd.asUInt,
      _.prmd      := prmd.asUInt,
      _.ecfg      := ecfg.asUInt,
      _.estat     := estat.asUInt,
      _.euen      := 0.U,
      _.era       := era,
      _.badv      := badv,
      _.eentry    := eentry ## 0.U(6.W),
      _.coreid    := 0.U,
      _.save0     := save(0),
      _.save1     := save(1),
      _.save2     := save(2),
      _.save3     := save(3),
      _.llbctl    := llbctl.asUInt,
      _.dmw0      := dmw(0).asUInt,
      _.dmw1      := dmw(1).asUInt,
      _.tid       := tid,
      _.tval      := tval,
      _.tcfg      := tcfg.asUInt,
      _.ticlr     := 0.U,
      _.asid      := asid.asUInt,
      _.tlbidx    := tlbidx.asUInt,
      _.tlbehi    := tlbehi ## 0.U(13.W),
      _.tlbelo0   := tlbelo0.asUInt,
      _.tlbelo1   := tlbelo1.asUInt,
      _.pgdl      := pgdl ## 0.U(12.W),
      _.pgdh      := pgdh ## 0.U(12.W),
      _.tlbrentry := tlbrentry ## 0.U(6.W)
    )
  } else difftestIO := DontCare

  for (i <- 0 until RegConf.writeCsrsPort) {
    when(io.csrsW.wen(i)) {
      when(io.csrsW.wcsr(i) === CRMD)      { crmd   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === PRMD)      { prmd   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === EUEN)      {}
      when(io.csrsW.wcsr(i) === ECFG)      { ecfg   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === ESTAT)     { estat  := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === ERA)       { era    := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === BADV)      { badv   := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === EENTRY)    { eentry := io.csrsW.wdata(i)(31, 6) }
      when(io.csrsW.wcsr(i) === CPUID)     {}
      SAVE zip save foreach { case (addr, save) =>
        when(io.csrsW.wcsr(i) === addr)    { save   := io.csrsW.wdata(i) }
      }
      when(io.csrsW.wcsr(i) === LLBCTL)    { llbctl := io.csrsW.wdata(i) }
      DMW zip dmw foreach { case (addr, dmw) =>
        when(io.csrsW.wcsr(i) === addr)    { dmw    := io.csrsW.wdata(i) }
      }
      when(io.csrsW.wcsr(i) === TID)       { tid    := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === TVAL)      {}
      when(io.csrsW.wcsr(i) === TCFG)      { tcfg := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === TICLR)     { when(io.csrsW.wdata(i)(0)) { estat.TIS := 0.B } }
      when(io.csrsW.wcsr(i) === ASID)      { asid := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === TLBIDX)    { tlbidx := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === TLBEHI)    { tlbehi := io.csrsW.wdata(i)(31, 13) }
      when(io.csrsW.wcsr(i) === TLBELO0)   { tlbelo0 := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === TLBELO1)   { tlbelo1 := io.csrsW.wdata(i) }
      when(io.csrsW.wcsr(i) === PGDL)      { pgdl := io.csrsW.wdata(i)(31, 12) }
      when(io.csrsW.wcsr(i) === PGDH)      { pgdh := io.csrsW.wdata(i)(31, 12) }
      when(io.csrsW.wcsr(i) === PGD)       {}
      when(io.csrsW.wcsr(i) === TLBRENTRY) { tlbrentry := io.csrsW.wdata(i)(31, 6) }

      when(io.csrsW.wcsr(i) === TCFG)    {
        val newTcfg = io.csrsW.wdata(i).asTypeOf(tcfg)
        tval := newTcfg.InitVal ## 0.U(2.W)
        when(newTcfg.En) { cnten := 1.B }
      }
    }
  }

  private val readPorts = io.csrsR ++ io.mmuRead
  readPorts.foreach(r => (r.rcsr, r.rdata) match { case (rcsr, rdata) => {
    rdata := 0.U
    when(rcsr === CRMD)      { rdata := crmd.asUInt }
    when(rcsr === PRMD)      { rdata := prmd.asUInt }
    when(rcsr === EUEN)      { rdata := 0.U }
    when(rcsr === ECFG)      { rdata := ecfg.asUInt }
    when(rcsr === ESTAT)     { rdata := estat.asUInt }
    when(rcsr === ERA)       { rdata := era }
    when(rcsr === BADV)      { rdata := badv }
    when(rcsr === EENTRY)    { rdata := eentry ## 0.U(6.W) }
    when(rcsr === CPUID)     { rdata := 0.U }
    SAVE zip save foreach { case (addr, save) =>
      when(rcsr === addr)    { rdata := save }
    }
    when(rcsr === LLBCTL)    { rdata := llbctl.asUInt }
    DMW zip dmw foreach { case (addr, dmw) =>
      when(rcsr === addr)    { rdata := dmw.asUInt }
    }
    when(rcsr === TID)       { rdata := tid }
    when(rcsr === TVAL)      { rdata := tval }
    when(rcsr === TCFG)      { rdata := tcfg.asUInt }
    when(rcsr === TICLR)     { rdata := 0.U }
    when(rcsr === ASID)      { rdata := asid.asUInt }
    when(rcsr === TLBIDX)    { rdata := tlbidx.asUInt }
    when(rcsr === TLBEHI)    { rdata := tlbehi ## 0.U(13.W) }
    when(rcsr === TLBELO0)   { rdata := tlbelo0.asUInt }
    when(rcsr === TLBELO1)   { rdata := tlbelo1.asUInt }
    when(rcsr === PGDL)      { rdata := pgdl ## 0.U(12.W) }
    when(rcsr === PGDH)      { rdata := pgdh ## 0.U(12.W) }
    when(rcsr === PGD)       { rdata := Mux(badv(31), pgdh, pgdl) ## 0.U(12.W) }
    when(rcsr === TLBRENTRY) { rdata := tlbrentry ## 0.U(6.W) }
  }})

  when(cnten) {
    tval := Mux(tval === 0.U, Mux(tcfg.Periodic, tcfg.InitVal ## 0.U(2.W), 0xffffffffL.U), tval - 1.U)
    when(tval === 0.U) {
      estat.TIS := 1.B
      cnten     := tcfg.Periodic
    }
  }

  estat.HWIS := io.interrupt.asBools

  when(laIOWrite.valid) {
    asid    := laIOWrite.asid
    tlbehi  := laIOWrite.tlbehi(31, 13)
    tlbelo0 := laIOWrite.tlbelo(0)
    tlbelo1 := laIOWrite.tlbelo(1)
    tlbidx  := laIOWrite.tlbidx
  }

  io.bareSEIP := DontCare; io.bareUEIP := DontCare
}

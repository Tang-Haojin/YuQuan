package cpu.pipeline

import math._
import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import utils._

class MEM(implicit p: Parameters) extends YQModule with cpu.cache.CacheParams {
  val io = IO(new MEMIO)

  private val mask    = Reg(UInt(8.W))
  private val addr    = Reg(UInt(valen.W))
  private val extType = Reg(UInt(3.W))

  private val rd      = RegInit(0.U(5.W)); io.output.rd     := rd
  private val data    = Reg(UInt(xlen.W)); io.output.data   := data
  private val isWcsr  = RegInit(0.B);      io.output.isWcsr := isWcsr
  private val wcsr    = Reg(Vec(RegConf.writeCsrsPort, UInt(12.W)));   io.output.wcsr    := wcsr
  private val csrData = Reg(Vec(RegConf.writeCsrsPort, UInt(xlen.W))); io.output.csrData := csrData
  private val retire  = Reg(Bool())
  private val priv    = RegInit("b11".U(2.W))
  private val isPriv  = RegInit(0.B)
  private val isSatp  = RegInit(0.B)
  private val isWfe   = RegInit(0.B)
  private val cause   = Reg(UInt(4.W))
  private val pc      = Reg(UInt(valen.W))
  private val except  = RegInit(0.B)
  private val flush   = RegInit(0.B)
  private val exit    = if (Debug) RegInit(0.U(3.W)) else null
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val mmio    = if (Debug) RegInit(0.B) else null
  private val intr    = if (Debug) RegInit(0.B) else null
  private val rvc     = if (Debug) RegInit(0.B) else null
  private val instr   = RegInit(0.U(32.W))
  private val diffStoreValid = RegInit(0.U(4.W))
  private val diffWLSPAddr   = RegInit(0.B); diffWLSPAddr := 0.B
  private val diffLSPAddr    = RegInit(0.U(alen.W))
  private val diffLSVAddr    = RegInit(0.U(valen.W))
  private val diffStoreData  = RegInit(0.U(xlen.W))
  private val diffLoadValid  = RegInit(0.U(6.W))
  private val allExcept      = RegInit(0.B)
  private val isEret         = RegInit(0.B)
  private val isRdcnt        = RegInit(0.B)
  private val isTLBFill      = RegInit(0.B)
  private val tlbFillIndex   = RegInit(0.U(log2Ceil(TlbEntries).W))
  private val counter        = RegInit(0.U(64.W))
  private val isHold         = RegInit(0.B)

  private val offset   = addr(axSize - 1, 0)

  private val wireOff = io.input.addr(axSize - 1, 0)

  private val NVALID  = RegInit(0.B); io.nextVR.VALID := NVALID
  private val LREADY  = RegInit(1.B); io.lastVR.READY := LREADY && io.nextVR.READY

  private val isMem = RegInit(0.B); private val wireIsMem = WireDefault(Bool(), isMem)
  private val rw    = RegInit(1.B); private val wireRw    = WireDefault(Bool(), rw)

  private val wireData  = WireDefault(UInt(xlen.W), data)
  private val wireAddr  = Mux(io.lastVR.VALID && io.lastVR.READY, io.input.addr, addr)
  private val wireMask  = WireDefault(UInt((xlen / 8).W), mask)
  private val wireRetr  = WireDefault(Bool(), io.input.retire)
  private val wireReql  = WireDefault(UInt(3.W), extType)
  private val wireTlbrw = WireDefault(0.B)
  private val wireFsh   = WireDefault(Bool(), !isLxb.B && flush)

  private val shiftRdata = VecInit((0 until xlen / 8).map(i => io.dmmu.pipelineResult.cpuResult.data >> (8 * i)))(offset)
  private val extRdata   = MuxLookup(extType(1, 0), shiftRdata(xlen - 1, 0))(if (xlen == 32) Seq(
    0.U -> Fill(xlen - 8 , ~extType(2) & shiftRdata(7 )) ## shiftRdata(7 , 0),
    1.U -> Fill(xlen - 16, ~extType(2) & shiftRdata(15)) ## shiftRdata(15, 0)
  ) else Seq(
    0.U -> Fill(xlen - 8 , ~extType(2) & shiftRdata(7 )) ## shiftRdata(7 , 0),
    1.U -> Fill(xlen - 16, ~extType(2) & shiftRdata(15)) ## shiftRdata(15, 0),
    2.U -> Fill(xlen - 32, ~extType(2) & shiftRdata(31)) ## shiftRdata(31, 0)
  ))

  private val rawStrb = VecInit((0 until log2Ceil(xlen) - 2).map { i => Fill(pow(2, i).round.toInt, 1.B) })(io.input.mask(1, 0))

  io.dmmu.pipelineReq.cpuReq.data   := wireData
  io.dmmu.pipelineReq.cpuReq.rw     := wireRw
  io.dmmu.pipelineReq.cpuReq.wmask  := wireMask
  io.dmmu.pipelineReq.cpuReq.valid  := wireIsMem
  io.dmmu.pipelineReq.cpuReq.addr   := wireAddr
  io.dmmu.pipelineReq.cpuReq.size   := wireReql(1, 0)
  io.dmmu.pipelineReq.cpuReq.revoke := DontCare
  io.dmmu.pipelineReq.flush         := wireFsh
  io.dmmu.pipelineReq.offset        := DontCare
  io.dmmu.pipelineReq.tlbOp         := wireReql
  io.dmmu.pipelineReq.tlbrw         := wireTlbrw
  io.dmmu.pipelineReq.rASID         := io.input.csrData(0)
  io.dmmu.pipelineReq.rVA           := io.input.csrData(1)
  io.dmmu.pipelineReq.cactlb        := !wireIsMem && !wireRw
  io.dmmu.pipelineReq.cpuReq.noCache.getOrElse(WireDefault(0.B)) := DontCare
  io.output.retire := retire
  io.output.priv   := priv
  io.output.isPriv := isPriv
  io.output.isSatp := isSatp
  io.output.except := except

  when(io.dmmu.pipelineResult.cpuResult.ready) {
    NVALID    := Mux(io.dmmu.pipelineResult.exception, 0.B, 1.B)
    LREADY    := 1.B
    isMem     := 0.B
    wireIsMem := 0.B
    rw        := 1.B
    wireRw    := 1.B
    flush := 0.B
    when(!rw) { data := extRdata }
    when(io.dmmu.pipelineResult.exception) {
      isHold := 0.B
      isWfe  := 1.B
      cause  := io.dmmu.pipelineResult.cause
    }.otherwise { if (Debug) mmio := io.dmmu.pipelineResult.isMMIO }
  }.elsewhen(isWfe && (!io.input.memExpt || io.input.cause =/= cause)) {
    LREADY    := 1.B
    NVALID    := 0.B // flush invalid instructions
    isMem     := 0.B
    wireIsMem := 0.B
    rw        := 1.B
    wireRw    := 1.B
    isHold    := 0.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) {
    rd        := io.input.rd
    wireData  := io.input.data
    wireMask  := VecInit((0 until xlen / 8).map(i => if (i == 0) rawStrb else rawStrb(xlen / 8 - 1 - i, 0) ## 0.U(i.W)))(wireOff)
    wireReql  := io.input.mask
    wireTlbrw := io.input.isTlbrw
    addr      := io.input.addr
    data      := wireData
    mask      := wireMask
    isWcsr    := io.input.isWcsr
    wcsr      := io.input.wcsr
    retire    := wireRetr
    csrData   := io.input.csrData
    extType   := wireReql
    priv      := io.input.priv
    isPriv    := io.input.isPriv
    isSatp    := io.input.isSatp
    isWfe     := 0.B
    except    := isWfe
    wireFsh   := io.input.fshTLB
    flush     := wireFsh
    when(isWfe) {
      if (isLxb) { csrData(3) := pc; csrData(4) := addr; csrData(5) := addr }
      else       { csrData(0) := pc; csrData(2) := addr }
    }.otherwise { pc := io.input.pc }
    if (Debug) {
      exit  := io.input.debug.exit
      rcsr  := io.input.debug.rcsr
      mmio  := 0.B
      intr  := io.input.debug.intr
      rvc   := io.input.debug.rvc
    }
    if (io.input.diff.isDefined) {
      val tlbRand = RegInit(0.U(log2Ceil(TlbEntries).W)); tlbRand := Mux(tlbRand === (TlbEntries - 1).U, 0.U, tlbRand + 1.U)
      isHold := !io.input.retire
      when(!isHold) {
        instr := io.input.diff.get.instr
        diffWLSPAddr := 1.B
        diffLSVAddr := io.input.addr
        diffLoadValid := Cat(
          0.B,
          io.input.isMem && io.input.isLd && io.input.mask(2, 0) === "b010".U, // LW
          io.input.isMem && io.input.isLd && io.input.mask(2, 0) === "b101".U, // LHU
          io.input.isMem && io.input.isLd && io.input.mask(2, 0) === "b001".U, // LH
          io.input.isMem && io.input.isLd && io.input.mask(2, 0) === "b100".U, // LBU
          io.input.isMem && io.input.isLd && io.input.mask(2, 0) === "b000".U  // LB
        )
        diffStoreValid := Cat(
          0.B,
          io.input.isMem && !io.input.isLd && io.input.mask(1, 0) === 2.U,
          io.input.isMem && !io.input.isLd && io.input.mask(1, 0) === 1.U,
          io.input.isMem && !io.input.isLd && io.input.mask(1, 0) === 0.U
        )
        diffStoreData := Mux(io.input.mask(1, 0) === 0.U, io.input.data(7, 0)  << (wireOff ## 0.U(3.W)),
                         Mux(io.input.mask(1, 0) === 1.U, io.input.data(15, 0) << (wireOff ## 0.U(3.W)),
                                                          io.input.data(31, 0)))
        allExcept := io.input.diff.get.allExcept
        isEret := io.input.diff.get.eret
        isRdcnt := io.input.diff.get.is_CNTinst
        isTLBFill := io.input.isTlbrw && io.input.mask(1, 0) === "b11".U
        tlbFillIndex := tlbRand
        counter := io.input.diff.get.timer_64_value
      }
    }
    when(io.input.isMem || io.input.isLd) {
      NVALID    := 0.B
      LREADY    := 0.B
      wireIsMem := io.input.isMem
      isMem     := io.input.isMem
      rw        := wireRw
      wireData  := VecInit((0 until xlen / 8).map(i => if (i == 0) io.input.data else io.input.data(xlen - 1 - (8 * i), 0) ## 0.U((8 * i).W)))(wireOff)
      wireRw    := !io.input.isLd
      if (isZmb) when(io.dmmu.pipelineResult.cpuResult.fastReady) {
        NVALID := 1.B
        LREADY := 1.B
        isMem  := 0.B
        rw     := 1.B
      }
    }.otherwise {
      NVALID := 1.B
      LREADY := 1.B
    }
  }.otherwise {
    NVALID := 0.B
    isSatp := 0.B
    isPriv := 0.B
  }

  when(diffWLSPAddr) { diffLSPAddr := io.dmmu.pipelineResult.paddr }

  if (Debug) io.output.debug.connect(
    _.exit := exit,
    _.pc   := pc,
    _.rcsr := rcsr,
    _.mmio := mmio,
    _.intr := intr,
    _.rvc  := rvc
  )
  if (io.output.diff.isDefined) io.output.diff.get.connect(
    _.instr          := instr,
    _.pc             := pc,
    _.lsPAddr        := diffLSPAddr,
    _.lsVAddr        := diffLSVAddr,
    _.loadValid      := diffLoadValid,
    _.storeValid     := diffStoreValid,
    _.storeData      := diffStoreData,
    _.allExcept      := allExcept,
    _.eret           := isEret,
    _.is_CNTinst     := isRdcnt,
    _.timer_64_value := counter,
    _.is_TLBFILL     := isTLBFill,
    _.TLBFILL_index  := tlbFillIndex
  )
}

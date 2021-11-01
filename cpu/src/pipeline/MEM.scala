package cpu.pipeline

import math._
import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

import cpu.cache._
import cpu.tools._

class MEM(implicit p: Parameters) extends YQModule {
  val io = IO(new MEMIO)

  private val mask    = RegInit(0.U(8.W))
  private val addr    = RegInit(0.U(valen.W))
  private val extType = RegInit(0.U(3.W))

  private val rd      = RegInit(0.U(5.W));    io.output.rd     := rd
  private val data    = RegInit(0.U(xlen.W)); io.output.data   := data
  private val isWcsr  = RegInit(0.B);         io.output.isWcsr := isWcsr
  private val wcsr    = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0xFFF.U(12.W)))); io.output.wcsr    := wcsr
  private val csrData = RegInit(VecInit(Seq.fill(RegConf.writeCsrsPort)(0.U(xlen.W))));   io.output.csrData := csrData
  private val retire  = RegInit(0.B)
  private val priv    = RegInit("b11".U(2.W))
  private val isPriv  = RegInit(0.B)
  private val isSatp  = RegInit(0.B)
  private val isWfe   = RegInit(0.B)
  private val cause   = RegInit(0.U(4.W))
  private val pc      = RegInit(0.U(valen.W))
  private val except  = RegInit(0.B)
  private val flush   = if (ext('S')) RegInit(0.B) else null
  private val exit    = if (Debug) RegInit(0.U(3.W)) else null
  private val rcsr    = if (Debug) RegInit(0xfff.U(12.W)) else null
  private val mmio    = if (Debug) RegInit(0.B) else null
  private val intr    = if (Debug) RegInit(0.B) else null
  private val rvc     = if (Debug) RegInit(0.B) else null

  private val offset   = addr(axSize - 1, 0)

  private val wireOff = io.input.addr(axSize - 1, 0)

  private val NVALID  = RegInit(0.B); io.nextVR.VALID := NVALID
  private val LREADY  = RegInit(1.B); io.lastVR.READY := LREADY && io.nextVR.READY

  private val isMem = RegInit(0.B); private val wireIsMem = WireDefault(Bool(), isMem)
  private val rw    = RegInit(0.B); private val wireRw    = WireDefault(Bool(), rw)

  private val wireData = WireDefault(UInt(xlen.W), data)
  private val wireAddr = WireDefault(UInt(valen.W), addr)
  private val wireMask = WireDefault(UInt((xlen / 8).W), mask)
  private val wireRetr = WireDefault(Bool(), io.input.retire)
  private val wireReql = WireDefault(UInt(3.W), extType)
  private val wireFsh  = if (ext('S')) WireDefault(Bool(), flush) else null

  private val shiftRdata = VecInit((0 until 8).map(i => io.dmmu.pipelineResult.cpuResult.data >> (8 * i)))(offset)
  private val extRdata   = VecInit((0 until 7).map {
    case 0 => Fill(xlen - 8 , shiftRdata(7 )) ## shiftRdata(7 , 0)
    case 1 => Fill(xlen - 16, shiftRdata(15)) ## shiftRdata(15, 0)
    case 2 => Fill(xlen - 32, shiftRdata(31)) ## shiftRdata(31, 0)
    case 4 => shiftRdata(7 , 0)
    case 5 => shiftRdata(15, 0)
    case 6 => shiftRdata(31, 0)
    case 3 if xlen >= 64 => shiftRdata(63, 0)
    case _ => 0.U(xlen.W)
  })(extType)

  private val rawStrb = VecInit((0 until 4).map { i => Fill(pow(2, i).round.toInt, 1.B) })(io.input.mask)

  io.dmmu.pipelineReq.cpuReq.data   := wireData
  io.dmmu.pipelineReq.cpuReq.rw     := wireRw
  io.dmmu.pipelineReq.cpuReq.wmask  := wireMask
  io.dmmu.pipelineReq.cpuReq.valid  := wireIsMem
  io.dmmu.pipelineReq.cpuReq.addr   := wireAddr
  io.dmmu.pipelineReq.cpuReq.size   := wireReql(1, 0)
  io.dmmu.pipelineReq.cpuReq.revoke := DontCare
  io.dmmu.pipelineReq.flush         := (if (ext('S')) wireFsh else 0.B)
  io.dmmu.pipelineReq.offset        := DontCare
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
    rw        := 0.B
    if (ext('S')) flush := 0.B
    when(!rw) { data := extRdata }
    when(io.dmmu.pipelineResult.exception) {
      isWfe := 1.B
      cause := io.dmmu.pipelineResult.cause
    }.otherwise { if (Debug) mmio := io.dmmu.pipelineResult.isMMIO }
  }.elsewhen(isWfe && (!io.input.except || io.input.cause =/= cause)) {
    LREADY    := 1.B
    NVALID    := 0.B // flush invalid instructions
    isMem     := 0.B
    wireIsMem := 0.B
    rw        := 0.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) {
    rd       := io.input.rd
    wireAddr := io.input.addr
    wireData := io.input.data
    wireMask := VecInit((0 until xlen / 8).map(i => if (i == 0) rawStrb else rawStrb(xlen / 8 - 1 - i, 0) ## 0.U(i.W)))(wireOff)
    wireReql := io.input.mask
    addr     := wireAddr
    data     := wireData
    mask     := wireMask
    isWcsr   := io.input.isWcsr
    wcsr     := io.input.wcsr
    retire   := wireRetr
    csrData  := io.input.csrData
    extType  := wireReql
    priv     := io.input.priv
    isPriv   := io.input.isPriv
    isSatp   := io.input.isSatp
    isWfe    := 0.B
    except   := isWfe
    when(isWfe) { csrData(0) := pc; csrData(2) := addr }
    .otherwise { pc := io.input.pc }
    if (ext('S')) {
      wireFsh := io.input.fshTLB
      flush   := wireFsh
    }
    if (Debug) {
      exit  := io.input.debug.exit
      rcsr  := io.input.debug.rcsr
      mmio  := 0.B
      intr  := io.input.debug.intr
      rvc   := io.input.debug.rvc
    }
    when(io.input.isMem) {
      NVALID    := 0.B
      LREADY    := 0.B
      wireIsMem := 1.B
      isMem     := 1.B
      rw        := wireRw
      wireData  := VecInit((0 until xlen / 8).map(i => if (i == 0) io.input.data else io.input.data(xlen - 1 - (8 * i), 0) ## 0.U((8 * i).W)))(wireOff)
      wireRw    := Mux(io.input.isLd, 0.B, 1.B)
    }.otherwise {
      NVALID := 1.B
      LREADY := 1.B
    }
  }.otherwise {
    NVALID := 0.B
    isSatp := 0.B
  }

  if (Debug) {
    io.output.debug.exit := exit
    io.output.debug.pc   := pc
    io.output.debug.rcsr := rcsr
    io.output.debug.mmio := mmio
    io.output.debug.intr := intr
    io.output.debug.rvc  := rvc
  }
}

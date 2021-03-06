package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class Bypass(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = new cpu.component.GPRsR
    val rregs  = Input(Vec(32, UInt(xlen.W)))
    val instr  = Input(UInt(32.W))
    val idOut  = new RdVal
    val exOut  = new RdVal
    val memOut = new RdVal
    val isLd   = Input (Bool())
    val isAmo  = Input (Bool())
    val isWait = Output(Bool())
  })

  private val insCmp = io.instr(1, 0)
  private val insCF3 = io.instr(15, 13)
  private val insCF2 = io.instr(11, 10)
  private val insRs  = Seq(io.instr(19, 15), io.instr(24, 20))
  private val insRsc = Seq(io.instr(11, 7), io.instr(6, 2))
  private val insRsp = Seq(1.U(2.W) ## io.instr(9, 7), 1.U(2.W) ## io.instr(4, 2))

  io.isWait := 0.B

  private val rregs = WireDefault(Vec(32, UInt(xlen.W)), io.rregs)
  for (i <- rregs.indices)
    when(i.U === 0.U && !io.isAmo) { rregs(i) := 0.U }
    .otherwise {
      when(i.U === io.exOut.index && io.exOut.valid) { rregs(i) := io.exOut.value }
      .elsewhen(i.U === io.memOut.index && io.memOut.valid) { rregs(i) := io.memOut.value }
    }
  (io.receive.rdata zip io.receive.raddr).foreach(x => x._1 := rregs(x._2))

  private def willWait(rs: Seq[UInt]): Unit =
    rs.foreach(x => when((x =/= 0.U || io.isAmo) && (
                          x === io.idOut.index && io.idOut.valid ||
                          x === io.exOut.index && io.exOut.valid && io.isLd)) { io.isWait := 1.B })

  private def willWait(rs: UInt): Unit = willWait(Seq(rs))

  if (isLxb) willWait(io.receive.raddr)
  else when(!ext('C').B || insCmp === "b11".U) { willWait(insRs) }
  if (ext('C')) when(insCmp === "b00".U) {
    when(insCF3 === "b000".U) { willWait(2.U) }
    .elsewhen(!insCF3(2)) { willWait(insRsp(0)) }
    .otherwise { willWait(insRsp) }
  }
  if (ext('C')) when(insCmp === "b01".U) {
    when(!insCF3(2)) { willWait(insRsc(0)) }
    .elsewhen(insCF3 === "b100".U) {
      when(insCF2.andR) { willWait(insRsp) }
      .otherwise { willWait(insRsp(0)) }
    }.elsewhen(insCF3(2, 1).andR) { willWait(insRsp(0)) }
  }
  if (ext('C')) when(insCmp === "b10".U) {
    when(insCF3(1)) { willWait(2.U); when(insCF3(2)) { willWait(insRsc(1)) } }
    when(insCF3 === "b100".U && insRsc(1) =/= 0.U) { willWait(insRsc(1)) }
    when(insCF3(1, 0) === "b00".U && insRsc(0) =/= 0.U) { willWait(insRsc(0)) }
  }

  when(io.isAmo && (
       io.idOut.valid ||
       io.idOut.index === io.exOut.index && io.exOut.valid && io.isLd)) { io.isWait := 1.B }
}

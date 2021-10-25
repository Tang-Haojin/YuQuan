package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class Bypass(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = new cpu.component.GPRsR
    val request = Flipped(new cpu.component.GPRsR)
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
  private val insRs  = VecInit(io.instr(19, 15), io.instr(24, 20))
  private val insRsc = VecInit(io.instr(11, 7), io.instr(6, 2))
  private val insRsp = VecInit(1.U(2.W) ## io.instr(9, 7), 1.U(2.W) ## io.instr(4, 2))

  io.isWait := 0.B
  io.request.raddr <> io.receive.raddr
  io.receive.rdata <> io.request.rdata
  for (i <- io.request.raddr.indices)
    when(io.receive.raddr(i) === 0.U && !io.isAmo) { io.receive.rdata(i) := 0.U }
    .otherwise {
      when(io.receive.raddr(i) === io.exOut.index && io.exOut.valid) {
        io.receive.rdata(i) := io.exOut.value
      }.elsewhen(io.receive.raddr(i) === io.memOut.index && io.memOut.valid) {
        io.receive.rdata(i) := io.memOut.value
      }
    }

  private def willWait(rs: Vec[UInt]): Unit =
    rs.foreach(x => when((x =/= 0.U || io.isAmo) && (
                          x === io.idOut.index && io.idOut.valid ||
                          x === io.exOut.index && io.exOut.valid && io.isLd)) { io.isWait := 1.B })

  private def willWait(rs: UInt): Unit = willWait(VecInit(rs))

  when(insCmp === "b11".U) { willWait(insRs) }
  if (ext('C')) when(insCmp === "b00".U) {
    when(insCF3 === "b000".U) { willWait(2.U) }
    .elsewhen(!insCF3(2)) { willWait(insRsp(0)) }
    .otherwise { willWait(insRsp) }
  }
  if (ext('C')) when(insCmp === "b01".U) {
    when(!insCF3(2)) { willWait(insRsc(0)) }
    .elsewhen(insCF3 === "b100".U) {
      when(insCF2.andR()) { willWait(insRsp) }
      .otherwise { willWait(insRsp(0)) }
    }.elsewhen(insCF3(2, 1).andR()) { willWait(insRsp(0)) }
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

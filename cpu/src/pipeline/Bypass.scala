package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import cpu.tools._

class Bypass(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = new cpu.component.GPRsR
    val request = Flipped(new cpu.component.GPRsR)
    val insRs  = Input(Vec(2, UInt(5.W)))
    val idOut  = new RdVal
    val exOut  = new RdVal
    val memOut = new RdVal
    val isLd   = Input (Bool())
    val isAmo  = Input (Bool())
    val isWait = Output(Bool())
  })

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
    
  io.insRs.foreach(x => when((x =/= 0.U || io.isAmo) && (
                              x === io.idOut.index && io.idOut.valid ||
                              x === io.exOut.index && io.exOut.valid && io.isLd)) { io.isWait := 1.B })
  when(io.isAmo && (
       io.idOut.valid ||
       io.idOut.index === io.exOut.index && io.exOut.valid && io.isLd)) { io.isWait := 1.B }
}

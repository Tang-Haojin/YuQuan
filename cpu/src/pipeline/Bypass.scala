package cpu.pipeline

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._

class Bypass(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val receive = new cpu.component.GPRsR
    val request = Flipped(new cpu.component.GPRsR)
    val idOut  = new RdVal
    val exOut  = new RdVal
    val memOut = new RdVal
    val isLd   = Input (Bool())
    val isWait = Output(Bool())
  })

  io.isWait := 0.B
  io.request.raddr := io.receive.raddr
  io.receive.raddr.foreach(x => when(x =/= 0.U && (x === io.idOut.index || (x === io.exOut.index && io.isLd))) { io.isWait := 1.B })
  for (i <- 0 until RegConf.readPortsNum)
    io.receive.rdata(i) := MuxLookup(io.receive.raddr(i), io.request.rdata(i), Seq(
      io.memOut.index -> io.memOut.value,
      io.exOut.index -> io.exOut.value,
      0.U -> 0.U
    ))
}

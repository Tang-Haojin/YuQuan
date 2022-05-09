package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.cache._

class DCacheMux(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val cpuIO    = new CpuIO(xlen)
    val dmaIO    = new CpuIO(xlen)
    val dcacheIO = Flipped(new CpuIO(xlen))
  })
  private val cpu::dma::Nil = Enum(2)
  private val idle::busy::Nil = Enum(2)
  private val current = RegInit(0.U(1.W))
  private val state = RegInit(UInt(1.W), idle)

  io.cpuIO.cpuResult := Mux(current === cpu, io.dcacheIO.cpuResult, 0.U.asTypeOf(new CpuResult(xlen)))
  io.dmaIO.cpuResult := Mux(current =/= cpu, io.dcacheIO.cpuResult, 0.U.asTypeOf(new CpuResult(xlen)))
  io.dcacheIO.cpuReq := Mux(current === cpu, io.cpuIO.cpuReq, io.dmaIO.cpuReq)

  when(state === idle || io.dcacheIO.cpuResult.ready) {
    io.dcacheIO.cpuReq := Mux(io.dmaIO.cpuReq.valid, io.dmaIO.cpuReq, io.cpuIO.cpuReq)
    current := Mux(io.dmaIO.cpuReq.valid, dma, cpu)
    state := io.dcacheIO.cpuReq.valid.asUInt
  }
}

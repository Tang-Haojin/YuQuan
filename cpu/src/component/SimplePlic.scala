package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import utils._

class SimplePlicIO extends SimpleRWIO(26, 32)

// only one interrupt on ontext 0 is supported
class SimplePlic(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val plicIO    = new SimplePlicIO
    val int       = Input(Bool())
    val eip       = Output(Bool())
  })

  private val used      = RegInit(0.B)
  private val priority  = RegInit(0.U(32.W))
  private val source    = RegInit(0.U(10.W))
  private val enable    = RegInit(0.B)
  private val threshold = RegInit(0.U(32.W))

  private val interrupt = RegNext(io.int, 0.B)

  io.eip := Mux(used, enable && (priority > threshold), 1.B) && interrupt
  io.plicIO.rdata := 0.U

  when(io.plicIO.addr >= SIMPLEPLIC.M_Priority(0).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.M_Pending(0).U(25, 0)) {
    when(io.plicIO.wen) { priority := io.plicIO.wdata }
    io.plicIO.rdata := priority
  }
  when(io.plicIO.addr >= SIMPLEPLIC.M_Pending(0).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.M_Enable(0, 0).U(25, 0)) {
    io.plicIO.rdata := UIntToOH(source(4, 0))
  }
  when(io.plicIO.addr >= SIMPLEPLIC.M_Enable(0, 0).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.M_Enable(0, 1).U(25, 0)) {
    when(io.plicIO.wen) { enable := io.plicIO.wdata.orR(); source := io.plicIO.addr(6, 2) ## OHToUInt(io.plicIO.wdata) }
    io.plicIO.rdata := enable.asUInt()
  }
  when(io.plicIO.addr === SIMPLEPLIC.M_Threshold(0).U(25, 0)) {
    when(io.plicIO.wen) { threshold := io.plicIO.wdata }
    io.plicIO.rdata := threshold
  }
  when(io.plicIO.addr === SIMPLEPLIC.M_CLAIM(0).U(25, 0)) {
    io.plicIO.rdata := source
  }
  
  when(io.plicIO.wen) { used := 1.B }
}

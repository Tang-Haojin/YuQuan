package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import utils._

class SimplePlicIO extends SimpleRWIO(26, 32)

// only one interrupt on hart 0 is supported
class SimplePlic(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val plicIO = new SimplePlicIO
    val int    = Input (Bool())
    val meip   = Output(Bool())
    val seip   = Output(Bool())
  })

  private val used        = RegInit(0.B)
  private val priority    = RegInit(0.U(32.W))
  private val source      = RegInit(0.U(10.W))
  private val m_enable    = RegInit(0.B)
  private val s_enable    = RegInit(0.B)
  private val m_threshold = RegInit(0.U(32.W))
  private val s_threshold = RegInit(0.U(32.W))

  private val interrupt = RegNext(io.int, 0.B)

  io.meip := Mux(used, m_enable && (priority > m_threshold), 1.B) && interrupt
  io.seip := Mux(used, s_enable && (priority > s_threshold), 1.B) && interrupt
  io.plicIO.rdata := 0.U

  when(io.plicIO.addr >= SIMPLEPLIC.Priority(0).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.Pending(0).U(25, 0)) {
    when(io.plicIO.wen) { priority := io.plicIO.wdata }
    io.plicIO.rdata := priority
  }
  when(io.plicIO.addr >= SIMPLEPLIC.Pending(0).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.Enable(0, 0).U(25, 0)) {
    io.plicIO.rdata := UIntToOH(source(4, 0)) & Fill(32, interrupt)
  }
  when(io.plicIO.addr >= SIMPLEPLIC.Enable(0, 0).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.Enable(0, 1).U(25, 0)) {
    when(io.plicIO.wen) { m_enable := io.plicIO.wdata.orR; source := io.plicIO.addr(6, 2) ## OHToUInt(io.plicIO.wdata) }
    io.plicIO.rdata := UIntToOH(source(4, 0)) & Fill(32, m_enable)
  }
  when(io.plicIO.addr >= SIMPLEPLIC.Enable(0, 1).U(25, 0) && io.plicIO.addr < SIMPLEPLIC.Enable(0, 2).U(25, 0)) {
    when(io.plicIO.wen) { s_enable := io.plicIO.wdata.orR; source := io.plicIO.addr(6, 2) ## OHToUInt(io.plicIO.wdata) }
    io.plicIO.rdata := UIntToOH(source(4, 0)) & Fill(32, s_enable)
  }
  when(io.plicIO.addr === SIMPLEPLIC.Threshold(0).U(25, 0)) {
    when(io.plicIO.wen) { m_threshold := io.plicIO.wdata }
    io.plicIO.rdata := m_threshold
  }
  when(io.plicIO.addr === SIMPLEPLIC.Threshold(1).U(25, 0)) {
    when(io.plicIO.wen) { s_threshold := io.plicIO.wdata }
    io.plicIO.rdata := s_threshold
  }
  when(io.plicIO.addr === SIMPLEPLIC.CLAIM(0).U(25, 0) || io.plicIO.addr === SIMPLEPLIC.CLAIM(1).U(25, 0)) {
    io.plicIO.rdata := source
  }
  
  when(io.plicIO.wen) { used := 1.B }
}

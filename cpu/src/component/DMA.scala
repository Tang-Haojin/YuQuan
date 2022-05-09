package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.cache._
import utils._

class DMA(implicit p: Parameters) extends YQModule {
  val io = IO(new Bundle {
    val cpuIO = Flipped(new CpuIO(xlen))
    val memIO = Flipped(new AXI_BUNDLE)
  })

  private val idle::busy::Nil = Enum(2)
  private val read::write::Nil = Enum(2)
  private val state = RegInit(UInt(1.W), idle)
  private val current = RegInit(UInt(1.W), read)

  private val reading = RegInit(0.B)
  private val writing = RegInit(0.B)

  private val AWREADY = RegInit(1.B); io.memIO.aw.ready := AWREADY
  private val WREADY  = RegInit(0.B); io.memIO.w .ready := WREADY
  private val BVALID  = RegInit(0.B); io.memIO.b .valid := BVALID
  private val ARREADY = RegInit(1.B); io.memIO.ar.ready := ARREADY
  private val RVALID  = RegInit(0.B); io.memIO.r .valid := RVALID
  private val ARSIZE  = RegInit(0.U(3.W))
  private val ARLEN   = RegInit(0.U(8.W))
  private val AWSIZE  = RegInit(0.U(3.W))
  private val AWLEN   = RegInit(0.U(8.W))

  private val RID    = RegInit(0.U(idlen.W)); io.memIO.r.bits.id := RID
  private val BID    = RegInit(0.U(idlen.W)); io.memIO.b.bits.id := BID
  private val ARADDR = RegInit(0.U(alen.W))
  private val AWADDR = RegInit(0.U(alen.W))

  private val RDATA = RegInit(0.U(xlen.W))
  private val WDATA = RegInit(0.U(xlen.W))
  private val WSTRB = RegInit(0.U((xlen / 8).W))

  private val wireRStep  = VecInit((0 until 8).map(1 << _).map(_.U))(ARSIZE)
  private val wireWStep  = VecInit((0 until 8).map(1 << _).map(_.U))(AWSIZE)

  io.memIO.b.bits.resp := 0.U
  io.memIO.b.bits.user := DontCare

  io.memIO.r.bits.last := 0.B
  io.memIO.r.bits.user := DontCare
  io.memIO.r.bits.resp := 0.U

  io.cpuIO.cpuReq.addr   := Mux(current === read, ARADDR, AWADDR)
  io.cpuIO.cpuReq.valid  := state === busy && Mux(current === read, !ARREADY && !RVALID, !AWREADY && !WREADY && !BVALID)
  io.cpuIO.cpuReq.data   := WDATA
  io.cpuIO.cpuReq.rw     := current === write
  io.cpuIO.cpuReq.wmask  := WSTRB
  io.cpuIO.cpuReq.revoke := 0.B
  io.cpuIO.cpuReq.size   := Mux(current === read, ARSIZE, AWSIZE)
  io.memIO.r.bits.data   := RDATA

  when(io.cpuIO.cpuResult.ready) {
    io.cpuIO.cpuReq.valid := 0.B
    when(current === read) { RVALID := 1.B; RDATA := io.cpuIO.cpuResult.data }
    .elsewhen(AWLEN =/= 0.U) { WREADY := 1.B; AWADDR := AWADDR + wireWStep; AWLEN := AWLEN - 1.U }
    .otherwise { BVALID := 1.B }
  }

  when(io.memIO.r.fire) {
    RVALID := 0.B
    when(ARLEN === 0.U) {
      ARREADY := 1.B
      io.memIO.r.bits.last := 1.B
      state := Mux(writing, busy, idle)
      reading := 0.B
      current := write
    }.otherwise {
      ARADDR := ARADDR + wireRStep
      ARLEN  := ARLEN - 1.U
    }
  }.elsewhen(io.memIO.r.valid && !io.memIO.r.ready && io.memIO.w.valid && current === read) { current := write }
  
  when(io.memIO.ar.fire) {
    current := read
    state   := busy
    reading := 1.B
    RID     := io.memIO.ar.bits.id
    ARADDR  := io.memIO.ar.bits.addr(alen - 1, axSize) ## 0.U(axSize.W)
    ARREADY := 0.B
    ARSIZE  := io.memIO.ar.bits.size
    ARLEN   := io.memIO.ar.bits.len
  }

  when(io.memIO.aw.fire) {
    current := write
    state   := busy
    writing := 1.B
    AWADDR  := io.memIO.aw.bits.addr(alen - 1, axSize) ## 0.U(axSize.W)
    BID     := io.memIO.aw.bits.id
    AWREADY := 0.B
    WREADY  := 1.B
    AWSIZE  := io.memIO.aw.bits.size
    AWLEN   := io.memIO.aw.bits.len
  }

  when(io.memIO.w.fire) {
    WREADY := 0.B
    WDATA  := io.memIO.w.bits.data
    WSTRB  := io.memIO.w.bits.strb
  }.elsewhen(io.memIO.w.ready && !io.memIO.w.valid && io.memIO.r.ready && current === write) { current := read }

  when(io.memIO.b.fire) {
    AWREADY := 1.B
    BVALID := 0.B
    state := Mux(reading && !(io.memIO.r.fire && io.memIO.r.bits.last), busy, idle)
    writing := 0.B
    current := read
  }
}

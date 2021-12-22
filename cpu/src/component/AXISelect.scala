package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class AXISelect(implicit p: Parameters) extends YQModule {
  val io = IO(new AxiSelectIO)

  private val mem::mmio::Nil = Enum(2)

  io.input <> io.RamIO
  io.RamIO.ar.valid := 0.B
  io.RamIO.r .ready := 0.B
  io.RamIO.aw.valid := 0.B
  io.RamIO.w .valid := 0.B
  io.RamIO.b .ready := 0.B

  io.input <> io.MMIO
  io.MMIO.ar.valid := 0.B
  io.MMIO.r .ready := 0.B
  io.MMIO.aw.valid := 0.B
  io.MMIO.w .valid := 0.B
  io.MMIO.b .ready := 0.B

  io.input.ar.valid := 0.B
  io.input.r .ready := 0.B
  io.input.aw.valid := 0.B
  io.input.w .valid := 0.B
  io.input.b .ready := 0.B

  private val AWREADY = RegInit(1.B)
  private val WREADY  = RegInit(1.B)
  private val BVALID  = RegInit(0.B)
  private val ARREADY = RegInit(1.B)
  private val RVALID  = RegInit(0.B)

  private val rdevice = RegInit(0.U(1.W))
  private val wdevice = RegInit(0.U(1.W))
  private val wireRdevice = WireDefault(UInt(1.W), rdevice)
  private val wireWdevice = WireDefault(UInt(1.W), wdevice)

  when(
    (io.input.ar.bits.addr >= DRAM.BASE.U) &&
    (io.input.ar.bits.addr < (DRAM.BASE + DRAM.SIZE).U)
  ) { wireRdevice := mem }.otherwise { wireRdevice := mmio }

  when(
    (io.input.ar.bits.addr >= DRAM.BASE.U) &&
    (io.input.ar.bits.addr < (DRAM.BASE + DRAM.SIZE).U)
  ) { wireWdevice := mem }.otherwise { wireWdevice := mmio }

  when((wireRdevice === mem) && ARREADY) {
    io.input.ar <> io.RamIO.ar
    io.input.ar.ready := io.RamIO.ar.ready
  }
  when((wireRdevice === mmio) && ARREADY) {
    io.input.ar <> io.MMIO.ar
    io.input.ar.ready := io.MMIO.ar.ready
  }

  when(rdevice === mem) {
    io.input.r <> io.RamIO.r
    io.input.r.valid := RVALID && io.RamIO.r.valid
  }
  when(rdevice === mmio) {
    io.input.r <> io.MMIO.r
    io.input.r.valid := RVALID && io.MMIO.r.valid
  }

  when((wireWdevice === mem) && AWREADY) {
    io.input.aw <> io.RamIO.aw
    io.input.aw.ready := io.input.aw.valid && io.RamIO.aw.ready
  }
  when((wireWdevice === mmio) && AWREADY) {
    io.input.aw <> io.MMIO.aw
    io.input.aw.ready := io.input.aw.valid && io.MMIO.aw.ready
  }
  when((wireWdevice === mem) && WREADY) {
    io.input.w <> io.RamIO.w
    io.input.w.ready := io.input.w.valid  && io.RamIO.w.ready
  }
  when((wireWdevice === mmio) && WREADY) {
    io.input.w <> io.MMIO.w
    io.input.w.ready := io.input.w.valid  && io.MMIO.w.ready
  }

  when(wdevice === mem) {
    io.input.b <> io.RamIO.b
    io.input.b.valid := BVALID && io.RamIO.b.valid
  }
  when(wdevice === mmio) {
    io.input.b <> io.MMIO.b
    io.input.b.valid := BVALID && io.MMIO.b.valid
  }

  when(io.input.r.fire) {
    when(io.input.r.bits.last) {
      ARREADY := 1.B
      RVALID  := 0.B
    }
  }.elsewhen(io.input.ar.fire) {
    ARREADY := 0.B
    RVALID  := 1.B
    rdevice := wireRdevice
  }

  when(io.input.aw.fire) {
    AWREADY := 0.B
    wdevice := wireWdevice
  }

  when(io.input.w.fire && io.input.w.bits.last) {
    // TODO: What if write data transfer before write address? We need a buffer.
    WREADY := 0.B
    BVALID := 1.B
  }

  when(io.input.b.fire) {
    AWREADY := 1.B
    WREADY  := 1.B
    BVALID  := 0.B
  }
}

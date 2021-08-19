package cpu.component

import chisel3._
import chisel3.util._

import tools._
import cpu.config.GeneralConfig._

class AXISelect extends Module {
  val io = IO(new AxiSelectIO)

  val mem::mmio::Nil = Enum(2)

  io.input <> io.RamIO
  io.RamIO.axiRa.ARVALID := 0.B
  io.RamIO.axiRd.RREADY  := 0.B
  io.RamIO.axiWa.AWVALID := 0.B
  io.RamIO.axiWd.WVALID  := 0.B
  io.RamIO.axiWr.BREADY  := 0.B

  io.input <> io.MMIO
  io.MMIO.axiRa.ARVALID := 0.B
  io.MMIO.axiRd.RREADY  := 0.B
  io.MMIO.axiWa.AWVALID := 0.B
  io.MMIO.axiWd.WVALID  := 0.B
  io.MMIO.axiWr.BREADY  := 0.B

  io.input.axiRa.ARREADY := 0.B
  io.input.axiRd.RVALID  := 0.B
  io.input.axiWa.AWREADY := 0.B
  io.input.axiWd.WREADY  := 0.B
  io.input.axiWr.BVALID  := 0.B

  val AWREADY = RegInit(1.B)
  val WREADY  = RegInit(1.B)
  val BVALID  = RegInit(0.B)
  val ARREADY = RegInit(1.B)
  val RVALID  = RegInit(0.B)

  val rdevice = RegInit(0.U(1.W))
  val wdevice = RegInit(0.U(1.W))
  val wireRdevice = WireDefault(UInt(1.W), rdevice)
  val wireWdevice = WireDefault(UInt(1.W), wdevice)

  when(
    (io.input.axiRa.ARADDR >= RamBase.U) &&
    (io.input.axiRa.ARADDR < (RamBase + RamSize).U)
  ) { wireRdevice := mem }.otherwise { wireRdevice := mmio }

  when(
    (io.input.axiWa.AWADDR >= RamBase.U) &&
    (io.input.axiWa.AWADDR < (RamBase + RamSize).U)
  ) { wireWdevice := mem }.otherwise { wireWdevice := mmio }

  when((wireRdevice === mem) && ARREADY) {
    io.input.axiRa <> io.RamIO.axiRa
    io.input.axiRa.ARREADY := io.RamIO.axiRa.ARREADY
  }
  when((wireRdevice === mmio) && ARREADY) {
    io.input.axiRa <> io.MMIO.axiRa
    io.input.axiRa.ARREADY := io.MMIO.axiRa.ARREADY
  }

  when(rdevice === mem) {
    io.input.axiRd <> io.RamIO.axiRd
    io.input.axiRd.RVALID := RVALID && io.RamIO.axiRd.RVALID
  }
  when(rdevice === mmio) {
    io.input.axiRd <> io.MMIO.axiRd
    io.input.axiRd.RVALID := RVALID && io.MMIO.axiRd.RVALID
  }

  when((wireWdevice === mem) && AWREADY) {
    io.input.axiWa <> io.RamIO.axiWa
    io.input.axiWa.AWREADY := io.input.axiWa.AWVALID && io.RamIO.axiWa.AWREADY
  }
  when((wireWdevice === mmio) && AWREADY) {
    io.input.axiWa <> io.MMIO.axiWa
    io.input.axiWa.AWREADY := io.input.axiWa.AWVALID && io.MMIO.axiWa.AWREADY
  }
  when((wireWdevice === mem) && WREADY) {
    io.input.axiWd <> io.RamIO.axiWd
    io.input.axiWd.WREADY := io.input.axiWd.WVALID  && io.RamIO.axiWd.WREADY
  }
  when((wireWdevice === mmio) && WREADY) {
    io.input.axiWd <> io.MMIO.axiWd
    io.input.axiWd.WREADY := io.input.axiWd.WVALID  && io.MMIO.axiWd.WREADY
  }

  when(wdevice === mem) {
    io.input.axiWr <> io.RamIO.axiWr
    io.input.axiWr.BVALID := BVALID && io.RamIO.axiWr.BVALID
  }
  when(wdevice === mmio) {
    io.input.axiWr <> io.MMIO.axiWr
    io.input.axiWr.BVALID := BVALID && io.MMIO.axiWr.BVALID
  }

  when(io.input.axiRd.RVALID && io.input.axiRd.RREADY) {
    when(io.input.axiRd.RLAST) {
      ARREADY := 1.B
      RVALID  := 0.B
    }
  }.elsewhen(io.input.axiRa.ARVALID && io.input.axiRa.ARREADY) {
    ARREADY := 0.B
    RVALID  := 1.B
    rdevice := wireRdevice
  }

  when(io.input.axiWa.AWVALID && io.input.axiWa.AWREADY) {
    AWREADY := 0.B
    wdevice := wireWdevice
  }

  when(io.input.axiWd.WVALID && io.input.axiWd.WREADY && io.input.axiWd.WLAST) {
    // TODO: What if write data transfer before write address? We need a buffer.
    WREADY := 0.B
    BVALID := 1.B
  }

  when(io.input.axiWr.BVALID && io.input.axiWr.BREADY) {
    AWREADY := 1.B
    WREADY  := 1.B
    BVALID  := 0.B
  }
}

package sim

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.config.GeneralConfig._
import cpu.MEM

class AxiSlaveIO extends Bundle {
  val basic = new BASIC
  val axiWa = Flipped(new AXIwa)
  val axiWd = Flipped(new AXIwd)
  val axiWr = Flipped(new AXIwr)
  val axiRa = Flipped(new AXIra)
  val axiRd = Flipped(new AXIrd)
}

class AxiRouterIO extends Bundle {
  val input   = new AxiSlaveIO
  val RamIO   = Flipped(new AxiSlaveIO)
  val Uart0IO = Flipped(new AxiSlaveIO)
}

class ROUTER extends RawModule {
  val io = IO(new AxiRouterIO)

  val mem::uart0::Nil = Enum(2)

  io.input <> io.RamIO
  io.RamIO.axiRa.ARVALID := 0.B
  io.RamIO.axiRd.RREADY  := 0.B
  io.RamIO.axiWa.AWVALID := 0.B
  io.RamIO.axiWd.WVALID  := 0.B
  io.RamIO.axiWr.BREADY  := 0.B

  io.input <> io.Uart0IO
  io.Uart0IO.axiRa.ARVALID := 0.B
  io.Uart0IO.axiRd.RREADY  := 0.B
  io.Uart0IO.axiWa.AWVALID := 0.B
  io.Uart0IO.axiWd.WVALID  := 0.B
  io.Uart0IO.axiWr.BREADY  := 0.B

  withClockAndReset(io.input.basic.ACLK, ~io.input.basic.ARESETn) {
    val AWREADY = RegInit(1.B);
    val WREADY  = RegInit(1.B);
    val BVALID  = RegInit(0.B);
    val ARREADY = RegInit(1.B);
    val RVALID  = RegInit(0.B);

    val rdevice = RegInit(0.U(4.W))
    val wdevice = RegInit(0.U(4.W))
    val wireRdevice = WireDefault(0.U(4.W))
    val wireWdevice = WireDefault(0.U(4.W))

    when(wireRdevice === mem) {
      io.input.axiRa <> io.RamIO.axiRa
      io.input.axiRa.ARREADY := ARREADY && io.RamIO.axiRa.ARREADY
    }.elsewhen(wireRdevice === uart0) {
      io.input.axiRa <> io.Uart0IO.axiRa
      io.input.axiRa.ARREADY := ARREADY && io.Uart0IO.axiRa.ARREADY
    }

    when(rdevice === mem) {
      io.input.axiRd <> io.RamIO.axiRd
      io.input.axiRd.RVALID := RVALID && io.RamIO.axiRd.RVALID
    }.elsewhen(rdevice === uart0) {
      io.input.axiRd <> io.Uart0IO.axiRd
      io.input.axiRd.RVALID := RVALID && io.Uart0IO.axiRd.RVALID
    }

    when(wireWdevice === mem) {
      io.input.axiWa <> io.RamIO.axiWa
      io.input.axiWd <> io.RamIO.axiWd
      io.input.axiWa.AWREADY := AWREADY && 
                                io.input.axiWa.AWVALID && 
                                io.input.axiWd.WVALID && 
                                io.RamIO.axiWa.AWREADY && 
                                io.RamIO.axiWd.WREADY
      io.input.axiWd.WREADY := io.input.axiWa.AWREADY
    }.elsewhen(wireWdevice === uart0) {
      io.input.axiWa <> io.Uart0IO.axiWa
      io.input.axiWd <> io.Uart0IO.axiWd
      io.input.axiWa.AWREADY := AWREADY && 
                                io.input.axiWa.AWVALID && 
                                io.input.axiWd.WVALID && 
                                io.Uart0IO.axiWa.AWREADY && 
                                io.Uart0IO.axiWd.WREADY
      io.input.axiWd.WREADY := io.input.axiWa.AWREADY
    }

    when(wdevice === mem) {
      io.input.axiWr <> io.RamIO.axiWr
      io.input.axiWr.BVALID := BVALID && io.RamIO.axiWr.BVALID
    }.elsewhen(wdevice === uart0) {
      io.input.axiWr <> io.Uart0IO.axiWr
      io.input.axiWr.BVALID := BVALID && io.Uart0IO.axiWr.BVALID
    }

    when(io.input.axiRd.RVALID && io.input.axiRd.RREADY) {
      ARREADY := 1.B
      RVALID  := 0.B
    }.elsewhen(io.input.axiRa.ARVALID && io.input.axiRa.ARREADY) {
      ARREADY := 0.B
      RVALID  := 1.B
      rdevice := wireRdevice
    }

    when(io.input.axiWa.AWVALID && io.input.axiWa.AWREADY) {
      AWREADY := 0.B
      BVALID  := 1.B
      wdevice := wireWdevice
    }

    when(io.input.axiWd.WVALID && io.input.axiWd.WREADY) {
      WREADY := 0.B
    }

    when(io.input.axiWr.BVALID && io.input.axiWr.BREADY) {
      AWREADY := 1.B
      WREADY  := 1.B
      BVALID  := 0.B
    }

    when(
      (io.input.axiRa.ARADDR >= MEMBase.U) &&
      (io.input.axiRa.ARADDR < (MEMBase + MEMSize).U)
    ) { wireRdevice := mem }
    .elsewhen(
      (io.input.axiRa.ARADDR >= UART0_MMIO.UART0_BASE.U) &&
      (io.input.axiRa.ARADDR < (UART0_MMIO.UART0_BASE + UART0_MMIO.UART0_SIZE).U)
    ) { wireRdevice := uart0 }

    when(
      (io.input.axiWa.AWADDR >= MEMBase.U) &&
      (io.input.axiWa.AWADDR < (MEMBase + MEMSize).U)
    ) { wireWdevice := mem }
    .elsewhen(
      (io.input.axiWa.AWADDR >= UART0_MMIO.UART0_BASE.U) &&
      (io.input.axiWa.AWADDR < (UART0_MMIO.UART0_BASE + UART0_MMIO.UART0_SIZE).U)
    ) { wireWdevice := uart0 }
  }
}

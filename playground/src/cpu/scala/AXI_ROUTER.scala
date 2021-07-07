package cpu

import chisel3._
import chisel3.util._

import tools.AxiSlaveIO
import cpu.config.GeneralConfig._

class AxiRouterIO extends Bundle {
  val input   = new AxiSlaveIO
  val RamIO   = Flipped(new AxiSlaveIO)
  val Uart0IO = Flipped(new AxiSlaveIO)
  val PLICIO  = Flipped(new AxiSlaveIO)
}

class ROUTER extends RawModule {
  val io = IO(new AxiRouterIO)

  val mem::uart0::plic::Nil = Enum(3)

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

  io.input <> io.PLICIO
  io.PLICIO.axiRa.ARVALID := 0.B
  io.PLICIO.axiRd.RREADY  := 0.B
  io.PLICIO.axiWa.AWVALID := 0.B
  io.PLICIO.axiWd.WVALID  := 0.B
  io.PLICIO.axiWr.BREADY  := 0.B

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
    }
    when(wireRdevice === uart0) {
      io.input.axiRa <> io.Uart0IO.axiRa
      io.input.axiRa.ARREADY := ARREADY && io.Uart0IO.axiRa.ARREADY
    }
    when(wireRdevice === plic) {
      io.input.axiRa <> io.PLICIO.axiRa
      io.input.axiRa.ARREADY := ARREADY && io.PLICIO.axiRa.ARREADY
    }

    when(rdevice === mem) {
      io.input.axiRd <> io.RamIO.axiRd
      io.input.axiRd.RVALID := RVALID && io.RamIO.axiRd.RVALID
    }
    when(rdevice === uart0) {
      io.input.axiRd <> io.Uart0IO.axiRd
      io.input.axiRd.RVALID := RVALID && io.Uart0IO.axiRd.RVALID
    }
    when(rdevice === plic) {
      io.input.axiRd <> io.PLICIO.axiRd
      io.input.axiRd.RVALID := RVALID && io.PLICIO.axiRd.RVALID
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
    }
    when(wireWdevice === uart0) {
      io.input.axiWa <> io.Uart0IO.axiWa
      io.input.axiWd <> io.Uart0IO.axiWd
      io.input.axiWa.AWREADY := AWREADY && 
                                io.input.axiWa.AWVALID && 
                                io.input.axiWd.WVALID && 
                                io.Uart0IO.axiWa.AWREADY && 
                                io.Uart0IO.axiWd.WREADY
      io.input.axiWd.WREADY := io.input.axiWa.AWREADY
    }
    when(wireWdevice === plic) {
      io.input.axiWa <> io.PLICIO.axiWa
      io.input.axiWd <> io.PLICIO.axiWd
      io.input.axiWa.AWREADY := AWREADY && 
                                io.input.axiWa.AWVALID && 
                                io.input.axiWd.WVALID && 
                                io.PLICIO.axiWa.AWREADY && 
                                io.PLICIO.axiWd.WREADY
      io.input.axiWd.WREADY := io.input.axiWa.AWREADY
    }

    when(wdevice === mem) {
      io.input.axiWr <> io.RamIO.axiWr
      io.input.axiWr.BVALID := BVALID && io.RamIO.axiWr.BVALID
    }
    when(wdevice === uart0) {
      io.input.axiWr <> io.Uart0IO.axiWr
      io.input.axiWr.BVALID := BVALID && io.Uart0IO.axiWr.BVALID
    }
    when(wdevice === plic) {
      io.input.axiWr <> io.PLICIO.axiWr
      io.input.axiWr.BVALID := BVALID && io.PLICIO.axiWr.BVALID
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
    when(
      (io.input.axiRa.ARADDR >= UART0_MMIO.UART0_BASE.U) &&
      (io.input.axiRa.ARADDR < (UART0_MMIO.UART0_BASE + UART0_MMIO.UART0_SIZE).U)
    ) { wireRdevice := uart0 }
    when(
      (io.input.axiRa.ARADDR >= PLIC.PLIC.U) &&
      (io.input.axiRa.ARADDR < (PLIC.PLIC + PLIC.PLIC_SIZE).U)
    ) { wireRdevice := plic }

    when(
      (io.input.axiWa.AWADDR >= MEMBase.U) &&
      (io.input.axiWa.AWADDR < (MEMBase + MEMSize).U)
    ) { wireWdevice := mem }
    when(
      (io.input.axiWa.AWADDR >= UART0_MMIO.UART0_BASE.U) &&
      (io.input.axiWa.AWADDR < (UART0_MMIO.UART0_BASE + UART0_MMIO.UART0_SIZE).U)
    ) { wireWdevice := uart0 }
    when(
      (io.input.axiWa.AWADDR >= PLIC.PLIC.U) &&
      (io.input.axiWa.AWADDR < (PLIC.PLIC + PLIC.PLIC_SIZE).U)
    ) { wireWdevice := plic }
  }
}

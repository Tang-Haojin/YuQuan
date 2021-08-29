package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

class AxiRouterIO(implicit val p: Parameters) extends Bundle with SimParams {
  val basic       = new BASIC
  val input       = Flipped(new AxiMasterChannel)
  val DramIO      = new AxiMasterChannel
  val UartIO      = new AxiMasterChannel
  val PLICIO      = new AxiMasterChannel
  val SpiIO       = new AxiMasterChannel
  val Nemu_UartIO = new AxiMasterChannel
}

class ROUTER(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new AxiRouterIO)

  val dram::uart::plic::spi::nemu_uart::Nil = Enum(5)

  for (i <- 2 until io.getElements.length) {
    val devIO = io.getElements.reverse(i).asInstanceOf[AxiMasterChannel]
    io.input <> devIO
    devIO.axiRa.ARVALID := 0.B
    devIO.axiRd.RREADY  := 0.B
    devIO.axiWa.AWVALID := 0.B
    devIO.axiWd.WVALID  := 0.B
    devIO.axiWr.BREADY  := 0.B
  }

  io.input.axiRa.ARREADY := 0.B
  io.input.axiRd.RVALID  := 0.B
  io.input.axiWa.AWREADY := 0.B
  io.input.axiWd.WREADY  := 0.B
  io.input.axiWr.BVALID  := 0.B

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B)
    val WREADY  = RegInit(1.B)
    val BVALID  = RegInit(0.B)
    val ARREADY = RegInit(1.B)
    val RVALID  = RegInit(0.B)

    val rdevice = RegInit(0.U(3.W))
    val wdevice = RegInit(0.U(3.W))
    val wireRdevice = WireDefault(0.U(3.W))
    val wireWdevice = WireDefault(0.U(3.W))

    def AddDevice(dev: UInt, devConf: MMAP, devIO: AxiMasterChannel): Unit = {
      when((wireRdevice === dev) && ARREADY) {
        io.input.axiRa <> devIO.axiRa
        io.input.axiRa.ARREADY := devIO.axiRa.ARREADY
      }

      when(rdevice === dev) {
        io.input.axiRd <> devIO.axiRd
        io.input.axiRd.RVALID := RVALID && devIO.axiRd.RVALID
      }

      when((wireWdevice === dev) && AWREADY) {
        io.input.axiWa <> devIO.axiWa
        io.input.axiWa.AWREADY := io.input.axiWa.AWVALID && devIO.axiWa.AWREADY
      }

      when((wireWdevice === dev) && WREADY) {
        io.input.axiWd <> devIO.axiWd
        io.input.axiWd.WREADY  := io.input.axiWd.WVALID  && devIO.axiWd.WREADY
      }

      when(wdevice === dev) {
        io.input.axiWr <> devIO.axiWr
        io.input.axiWr.BVALID := BVALID && devIO.axiWr.BVALID
      }

      when(
        (io.input.axiRa.ARADDR >= devConf.BASE.U) &&
        (io.input.axiRa.ARADDR < (devConf.BASE + devConf.SIZE).U)
      ) { wireRdevice := dev }

      when(
        (io.input.axiWa.AWADDR >= devConf.BASE.U) &&
        (io.input.axiWa.AWADDR < (devConf.BASE + devConf.SIZE).U)
      ) { wireWdevice := dev }
    }

    AddDevice(dram, DRAM, io.DramIO)
    AddDevice(uart, UART, io.UartIO)
    AddDevice(plic, PLIC, io.PLICIO)
    AddDevice(spi , SPI , io.SpiIO )
    AddDevice(nemu_uart, NEMU_UART, io.Nemu_UartIO)

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
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.input.axiWr.BVALID && io.input.axiWr.BREADY) {
      AWREADY := 1.B
      WREADY  := 1.B
      BVALID  := 0.B
    }
  }
}

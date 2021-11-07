package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._

class AxiRouterIO(implicit val p: Parameters) extends Bundle with SimParams {
  val basic       = new BASIC
  val input       = Flipped(new AXI_BUNDLE)
  val DramIO      = new AXI_BUNDLE
  val UartIO      = new AXI_BUNDLE
  val PLICIO      = new AXI_BUNDLE
  val SpiIO       = new AXI_BUNDLE
  val Nemu_UartIO = new AXI_BUNDLE
  val SdIO        = new AXI_BUNDLE
}

class ROUTER(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new AxiRouterIO)

  val dram::uart::plic::spiflash::nemu_uart::sd_card::Nil = Enum(6)

  for (i <- 2 until io.getElements.length) {
    val devIO = io.getElements.reverse(i).asInstanceOf[AXI_BUNDLE]
    io.input <> devIO
    devIO.ar.valid := 0.B
    devIO.r .ready := 0.B
    devIO.aw.valid := 0.B
    devIO.w .valid := 0.B
    devIO.b .ready := 0.B
  }

  io.input.ar.ready := 0.B
  io.input.r .valid := 0.B
  io.input.aw.ready := 0.B
  io.input.w .ready := 0.B
  io.input.b .valid := 0.B

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

    def AddDevice(dev: UInt, devConf: MMAP, devIO: AXI_BUNDLE): Unit = {
      when((wireRdevice === dev) && ARREADY) {
        io.input.ar <> devIO.ar
        io.input.ar.ready := devIO.ar.ready
      }

      when(rdevice === dev) {
        io.input.r <> devIO.r
        io.input.r.valid := RVALID && devIO.r.valid
      }

      when((wireWdevice === dev) && AWREADY) {
        io.input.aw <> devIO.aw
        io.input.aw.ready := io.input.aw.valid && devIO.aw.ready
      }

      when((wireWdevice === dev) && WREADY) {
        io.input.w <> devIO.w
        io.input.w.ready  := io.input.w.valid  && devIO.w.ready
      }

      when(wdevice === dev) {
        io.input.b <> devIO.b
        io.input.b.valid := BVALID && devIO.b.valid
      }

      when(
        (io.input.ar.bits.addr >= devConf.BASE.U) &&
        (io.input.ar.bits.addr < (devConf.BASE + devConf.SIZE).U)
      ) { wireRdevice := dev }

      when(
        (io.input.aw.bits.addr >= devConf.BASE.U) &&
        (io.input.aw.bits.addr < (devConf.BASE + devConf.SIZE).U)
      ) { wireWdevice := dev }
    }

    AddDevice(dram, DRAM, io.DramIO)
    AddDevice(uart, UART, io.UartIO)
    AddDevice(plic, PLIC, io.PLICIO)
    AddDevice(spiflash, SPIFLASH, io.SpiIO )
    AddDevice(nemu_uart, NEMU_UART, io.Nemu_UartIO)
    AddDevice(sd_card, SD_CARD, io.SdIO)

    when(io.input.r.fire()) {
      when(io.input.r.bits.last) {
        ARREADY := 1.B
        RVALID  := 0.B
      }
    }.elsewhen(io.input.ar.fire()) {
      ARREADY := 0.B
      RVALID  := 1.B
      rdevice := wireRdevice
    }

    when(io.input.aw.fire()) {
      AWREADY := 0.B
      wdevice := wireWdevice
    }

    when(io.input.w.fire() && io.input.w.bits.last) {
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.input.b.fire()) {
      AWREADY := 1.B
      WREADY  := 1.B
      BVALID  := 0.B
    }
  }
}

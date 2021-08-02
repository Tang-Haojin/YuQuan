package cpu.peripheral

import chisel3._
import chisel3.util._
import java.io.File

import tools._
import cpu.config.GeneralConfig._
import uart_config._

object uart_config {
  val DATA_BUS_WIDTH_8 = true
  val UART_ADDR_WIDTH = if (DATA_BUS_WIDTH_8) 3 else 5
  val UART_DATA_WIDTH = if (DATA_BUS_WIDTH_8) 8 else 32
}

class UregIO extends Bundle {
  val clk          = Input (Clock())
  val wb_rst_i     = Input (Bool())
  val wb_addr_i    = Input (UInt(UART_ADDR_WIDTH.W))
  val wb_dat_i     = Input (UInt(8.W))
  val wb_dat_o     = Output(UInt(8.W))
  val wb_we_i      = Input (Bool())
  val wb_re_i      = Input (Bool())
  val modem_inputs = Input (UInt(4.W))
  val stx_pad_o    = Output(UInt(1.W))
  val srx_pad_i    = Input (UInt(1.W))
  val rts_pad_o    = Output(UInt(1.W))
  val dtr_pad_o    = Output(UInt(1.W))
  val int_o        = Output(Bool())
}

class UartAxiSlaveIO extends AxiSlaveIO {
  val interrupt = Output(Bool())    // interrupt request (active-high)
  val srx       = Input (UInt(1.W))
  val stx       = Output(UInt(1.W))
}

class uart_regs extends BlackBox with HasBlackBoxPath {
  val io = IO(new UregIO)
  addPath(new File("playground/src/cpu/vsrc/peripheral/uart16550/uart_regs.v").getCanonicalPath)
}

class Uart16550 extends RawModule {
  val io = IO(new UartAxiSlaveIO)

  val ctsn = 0.B
  val dsr_pad_i = 0.B
  val ri_pad_i = 0.B
  val dcd_pad_i = 0.B

  io.axiWr.BRESP := 0.U
  io.axiWr.BUSER := DontCare

  io.axiRd.RLAST := 1.B
  io.axiRd.RUSER := DontCare
  io.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(IDLEN.W)); io.axiRd.RID := RID
    val BID    = RegInit(0.U(IDLEN.W)); io.axiWr.BID := BID
    val ARADDR = RegInit(0.U(3.W))
    val AWADDR = RegInit(0.U(3.W))

    val wireARADDR = WireDefault(UInt(3.W), ARADDR)

    val uregs = Module(new uart_regs)
    uregs.io.clk := io.basic.ACLK
    uregs.io.wb_rst_i := ~io.basic.ARESETn
    uregs.io.wb_addr_i := wireARADDR
    uregs.io.wb_dat_i := VecInit((0 until 8).map { i => io.axiWd.WDATA >> (8 * i) })(AWADDR)
    uregs.io.wb_we_i := 0.B
    uregs.io.wb_re_i := 0.B
    uregs.io.modem_inputs := Cat(~ctsn, dsr_pad_i, ri_pad_i, dcd_pad_i)
    uregs.io.srx_pad_i := io.srx
    io.axiRd.RDATA := VecInit((0 until 8).map { i => uregs.io.wb_dat_o << (8 * i) })(ARADDR)
    io.stx := uregs.io.stx_pad_o
    io.interrupt := uregs.io.int_o
    when(uregs.io.wb_we_i) { uregs.io.wb_addr_i := AWADDR }

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      uregs.io.wb_re_i := 1.B
      wireARADDR := io.axiRa.ARADDR
      ARADDR  := wireARADDR
      RID     := io.axiRa.ARID
      ARREADY := 0.B
      RVALID  := 1.B
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWADDR  := io.axiWa.AWADDR
      BID     := io.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      uregs.io.wb_we_i := 1.B
      WREADY := 0.B
      BVALID := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}

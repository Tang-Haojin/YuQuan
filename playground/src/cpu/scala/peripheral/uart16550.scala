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

  io.axiWr.BID := 1.U // since only cpu requests writing now
  io.axiWr.BRESP := DontCare
  io.axiWr.BUSER := DontCare

  io.axiRd.RLAST := 1.B
  io.axiRd.RUSER := DontCare
  io.axiRd.RRESP := DontCare

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(1.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
                                io.axiRa.ARREADY := 1.B
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(4.W)); io.axiRd.RID := RID
    val WDATA  = RegInit(0.U(XLEN.W))
    val WADDR  = RegInit(0.U(XLEN.W))
    val RDATA  = RegInit(0.U(8.W))

    val uregs = Module(new uart_regs)
    uregs.io.clk := io.basic.ACLK
    uregs.io.wb_rst_i := ~io.basic.ARESETn
    uregs.io.wb_addr_i := 0.U
    uregs.io.wb_dat_i := WDATA
    uregs.io.wb_we_i := 0.B
    uregs.io.wb_re_i := 0.B
    uregs.io.modem_inputs := Cat(~ctsn, dsr_pad_i, ri_pad_i, dcd_pad_i)
    uregs.io.srx_pad_i := io.srx
    io.axiRd.RDATA := Cat(Fill(XLEN - 8, 0.U), RDATA)
    io.stx := uregs.io.stx_pad_o
    io.interrupt := uregs.io.int_o

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      uregs.io.wb_re_i := 1.B
      uregs.io.wb_addr_i := io.axiRa.ARADDR - UART0_MMIO.BASE.U
      RID := io.axiRa.ARID
      RVALID := 1.B
      RDATA := uregs.io.wb_dat_o
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWREADY := 0.B
      WADDR := io.axiWa.AWADDR
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      WDATA  := io.axiWd.WDATA
      WREADY := 0.B
    }

    when(~io.axiWa.AWREADY && ~io.axiWd.WREADY) {
      AWREADY := 1.B
      WREADY  := 1.B
      uregs.io.wb_we_i := 1.B
      uregs.io.wb_addr_i := WADDR - UART0_MMIO.BASE.U
      BVALID  := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }
  }
}

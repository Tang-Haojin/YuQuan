package peripheral.uart16550

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import java.io.File

import uart_config._
import utils._
import peripheral._

object uart_config {
  val DATA_BUS_WIDTH_8 = true
  val UART_ADDR_WIDTH = if (DATA_BUS_WIDTH_8) 3 else 5
  val UART_DATA_WIDTH = if (DATA_BUS_WIDTH_8) 8 else 32
}

class UregIO(implicit p: Parameters) extends Bundle {
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

trait UartASICIOTrait {
  val interrupt = Output(Bool())    // interrupt request (active-high)
  val srx       = Input (UInt(1.W))
  val stx       = Output(UInt(1.W))
}

class UartAxiSlaveIO(implicit p: Parameters) extends AxiSlaveIO with UartASICIOTrait

class uart_regs(implicit p: Parameters) extends BlackBox with HasBlackBoxPath {
  val io = IO(new UregIO)
  addPath(new File("peripheral/src/uart16550/uart16550/uart_regs.v").getCanonicalPath)
}

class Uart16550(implicit val p: Parameters) extends RawModule with PeripheralParams {
  val io = IO(new UartAxiSlaveIO)

  val ctsn = 0.B
  val dsr_pad_i = 0.B
  val ri_pad_i = 0.B
  val dcd_pad_i = 0.B

  io.channel.axiWr.BRESP := 0.U
  io.channel.axiWr.BUSER := DontCare

  io.channel.axiRd.RLAST := 1.B
  io.channel.axiRd.RUSER := DontCare
  io.channel.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(0.B); io.channel.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.channel.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.channel.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.channel.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(idlen.W)); io.channel.axiRd.RID := RID
    val BID    = RegInit(0.U(idlen.W)); io.channel.axiWr.BID := BID
    val ARADDR = RegInit(0.U(3.W))
    val AWADDR = RegInit(0.U(3.W))

    val wireARADDR = WireDefault(UInt(3.W), ARADDR)
    val wdata = RegInit(0.U(8.W)); val wireWdata = WireDefault(UInt(8.W), wdata)

    //--------------------------------------------------------
    // Registers
    // As shown below reg_dat_i should be stable
    // one-cycle after reg_we negates.
    //              ___     ___     ___     ___     ___     ___
    //  clk      __|   |___|   |___|   |___|   |___|   |___|   |__
    //             ________________        ________________
    //  reg_adr  XX________________XXXXXXXX________________XXXX
    //             ________________
    //  reg_dat_i X________________XXXXXXX
    //                                     ________________
    //  reg_dat_o XXXXXXXXXXXXXXXXXXXXXXXXX________________XXXX
    //                                              _______
    //  reg_re   __________________________________|       |_____
    //              _______
    //  reg_we   __|       |_____________________________________
    //
    val uregs = Module(new uart_regs)
    uregs.io.clk := io.basic.ACLK
    uregs.io.wb_rst_i := ~io.basic.ARESETn
    uregs.io.wb_addr_i := wireARADDR
    uregs.io.wb_dat_i := wireWdata
    uregs.io.wb_we_i := 0.B
    uregs.io.wb_re_i := 0.B
    uregs.io.modem_inputs := Cat(~ctsn, dsr_pad_i, ri_pad_i, dcd_pad_i)
    uregs.io.srx_pad_i := io.srx
    io.channel.axiRd.RDATA := VecInit((0 until 8).map { i => uregs.io.wb_dat_o << (8 * i) })(ARADDR)
    io.stx := uregs.io.stx_pad_o
    io.interrupt := uregs.io.int_o
    when(uregs.io.wb_we_i) { uregs.io.wb_addr_i := AWADDR }

    when(io.channel.axiRd.RVALID && io.channel.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.axiRa.ARVALID && io.channel.axiRa.ARREADY) {
      uregs.io.wb_re_i := 1.B
      wireARADDR := io.channel.axiRa.ARADDR
      ARADDR  := wireARADDR
      RID     := io.channel.axiRa.ARID
      ARREADY := 0.B
      RVALID  := 1.B
    }

    when(io.channel.axiWa.AWVALID && io.channel.axiWa.AWREADY) {
      AWADDR  := io.channel.axiWa.AWADDR
      BID     := io.channel.axiWa.AWID
      AWREADY := 0.B
      WREADY  := 1.B
    }

    when(io.channel.axiWd.WVALID && io.channel.axiWd.WREADY) {
      uregs.io.wb_we_i := 1.B
      wireWdata := VecInit((0 until 8).map { i => io.channel.axiWd.WDATA >> (8 * i) })(AWADDR)
      wdata     := wireWdata
      WREADY    := 0.B
      BVALID    := 1.B
    }

    when(io.channel.axiWr.BVALID && io.channel.axiWr.BREADY) {
      AWREADY := 1.B
      BVALID  := 0.B
    }
  }
}

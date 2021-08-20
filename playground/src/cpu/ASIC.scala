package cpu

import chisel3._
import chisel3.util._

import tools._
import peripheral.spi._
import peripheral.chiplink._
import peripheral._
import config.Debug._
import config.GeneralConfig._

class ASIC extends RawModule {
  val io = IO(new Bundle {
    val basic = new BASIC
    val UartIO = new Bundle {
      val srx = Input (UInt(1.W))
      val stx = Output(UInt(1.W))
    }
    val SpiIO = new SpiMasterIO
    val ChiplinkIO = new ChiplinkIO
    val debug = if (Debug) new DEBUG else null
  })
  
  val cpu       = Module(new CPU)
  val router    = Module(new AsicXbar)
  val uartCtrl  = Module(new Uart16550)
  val spiCtrl   = Module(new spi_axi_flash)
  val plic      = Module(new Plic)
  val chiplink  = Module(new ChiplinkBridge)

  io.UartIO.stx   := uartCtrl.io.stx
  uartCtrl.io.srx := io.UartIO.srx
  io.SpiIO <> spiCtrl.io.spi_m
  io.ChiplinkIO.c2b.clk  := chiplink.io.fpga_io_c2b_clk
  io.ChiplinkIO.c2b.rst  := chiplink.io.fpga_io_c2b_rst
  io.ChiplinkIO.c2b.send := chiplink.io.fpga_io_c2b_send
  io.ChiplinkIO.c2b.data := chiplink.io.fpga_io_c2b_data
  chiplink.io.fpga_io_b2c_clk  := io.ChiplinkIO.b2c.clk
  chiplink.io.fpga_io_b2c_rst  := io.ChiplinkIO.b2c.rst
  chiplink.io.fpga_io_b2c_send := io.ChiplinkIO.b2c.send
  chiplink.io.fpga_io_b2c_data := io.ChiplinkIO.b2c.data
  
  cpu.io.mmioAXI <> router.io.input

  router.io.UartIO <> uartCtrl.io.channel
  router.io.PLICIO <> plic.io.channel
  router.io.SpiIO  <> spiCtrl.io.axi_s.channel

  LinkMEM (chiplink.io, cpu.io.memAXI)
  LinkMMIO(chiplink.io, router.io.ChiplinkIO)
  LinkDMA (chiplink.io, cpu.io.dmaAXI)

  plic.io.inter     := VecInit(Seq.fill(plic.io.inter.length)(0.B))
  plic.io.inter(10) := uartCtrl.io.interrupt
  cpu.io.intr       := plic.io.eip

  cpu.io.basic.ACLK              := io.basic.ACLK
  cpu.io.basic.ARESETn           := io.basic.ARESETn
  router.io.basic.ACLK           := io.basic.ACLK
  router.io.basic.ARESETn        := io.basic.ARESETn
  uartCtrl.io.basic.ACLK         := io.basic.ACLK
  uartCtrl.io.basic.ARESETn      := io.basic.ARESETn
  spiCtrl.io.axi_s.basic.ACLK    := io.basic.ACLK
  spiCtrl.io.axi_s.basic.ARESETn := io.basic.ARESETn
  plic.io.basic.ACLK             := io.basic.ACLK
  plic.io.basic.ARESETn          := io.basic.ARESETn
  chiplink.io.clock              := io.basic.ACLK.asBool
  chiplink.io.reset              := !io.basic.ARESETn

  if (Debug) io.debug <> cpu.io.debug
}

private class LinkMEM(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel) {
  chiplinkIO.slave_axi4_mem_0_awvalid := cpuIO.axiWa.AWVALID
  chiplinkIO.slave_axi4_mem_0_awid    := cpuIO.axiWa.AWID
  chiplinkIO.slave_axi4_mem_0_awaddr  := cpuIO.axiWa.AWADDR
  chiplinkIO.slave_axi4_mem_0_awlen   := cpuIO.axiWa.AWLEN
  chiplinkIO.slave_axi4_mem_0_awsize  := cpuIO.axiWa.AWSIZE
  chiplinkIO.slave_axi4_mem_0_awburst := cpuIO.axiWa.AWBURST

  chiplinkIO.slave_axi4_mem_0_wvalid := cpuIO.axiWd.WVALID
  chiplinkIO.slave_axi4_mem_0_wdata  := cpuIO.axiWd.WDATA
  chiplinkIO.slave_axi4_mem_0_wstrb  := cpuIO.axiWd.WSTRB
  chiplinkIO.slave_axi4_mem_0_wlast  := cpuIO.axiWd.WLAST

  chiplinkIO.slave_axi4_mem_0_bready := cpuIO.axiWr.BREADY

  chiplinkIO.slave_axi4_mem_0_arvalid := cpuIO.axiRa.ARVALID
  chiplinkIO.slave_axi4_mem_0_arid    := cpuIO.axiRa.ARID
  chiplinkIO.slave_axi4_mem_0_araddr  := cpuIO.axiRa.ARADDR
  chiplinkIO.slave_axi4_mem_0_arlen   := cpuIO.axiRa.ARLEN
  chiplinkIO.slave_axi4_mem_0_arsize  := cpuIO.axiRa.ARSIZE
  chiplinkIO.slave_axi4_mem_0_arburst := cpuIO.axiRa.ARBURST

  chiplinkIO.slave_axi4_mem_0_rready := cpuIO.axiRd.RREADY

  cpuIO.axiWa.AWREADY := chiplinkIO.slave_axi4_mem_0_awready
  cpuIO.axiWd.WREADY  := chiplinkIO.slave_axi4_mem_0_wready
  cpuIO.axiWr.BVALID  := chiplinkIO.slave_axi4_mem_0_bvalid
  cpuIO.axiWr.BID     := chiplinkIO.slave_axi4_mem_0_bid
  cpuIO.axiWr.BRESP   := chiplinkIO.slave_axi4_mem_0_bresp
  cpuIO.axiRa.ARREADY := chiplinkIO.slave_axi4_mem_0_arready
  cpuIO.axiRd.RVALID  := chiplinkIO.slave_axi4_mem_0_rvalid
  cpuIO.axiRd.RID     := chiplinkIO.slave_axi4_mem_0_rid
  cpuIO.axiRd.RDATA   := chiplinkIO.slave_axi4_mem_0_rdata
  cpuIO.axiRd.RRESP   := chiplinkIO.slave_axi4_mem_0_rresp
  cpuIO.axiRd.RLAST   := chiplinkIO.slave_axi4_mem_0_rlast

  cpuIO.axiWr.BUSER := DontCare
  cpuIO.axiRd.RUSER := DontCare
}

private object LinkMEM {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel): LinkMEM = new LinkMEM(chiplinkIO, cpuIO)
}

private class LinkMMIO(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel) {
  chiplinkIO.slave_axi4_mmio_0_awvalid := cpuIO.axiWa.AWVALID
  chiplinkIO.slave_axi4_mmio_0_awid    := cpuIO.axiWa.AWID
  chiplinkIO.slave_axi4_mmio_0_awaddr  := cpuIO.axiWa.AWADDR
  chiplinkIO.slave_axi4_mmio_0_awlen   := cpuIO.axiWa.AWLEN
  chiplinkIO.slave_axi4_mmio_0_awsize  := cpuIO.axiWa.AWSIZE
  chiplinkIO.slave_axi4_mmio_0_awburst := cpuIO.axiWa.AWBURST

  chiplinkIO.slave_axi4_mmio_0_wvalid := cpuIO.axiWd.WVALID
  chiplinkIO.slave_axi4_mmio_0_wdata  := cpuIO.axiWd.WDATA
  chiplinkIO.slave_axi4_mmio_0_wstrb  := cpuIO.axiWd.WSTRB
  chiplinkIO.slave_axi4_mmio_0_wlast  := cpuIO.axiWd.WLAST

  chiplinkIO.slave_axi4_mmio_0_bready := cpuIO.axiWr.BREADY

  chiplinkIO.slave_axi4_mmio_0_arvalid := cpuIO.axiRa.ARVALID
  chiplinkIO.slave_axi4_mmio_0_arid    := cpuIO.axiRa.ARID
  chiplinkIO.slave_axi4_mmio_0_araddr  := cpuIO.axiRa.ARADDR
  chiplinkIO.slave_axi4_mmio_0_arlen   := cpuIO.axiRa.ARLEN
  chiplinkIO.slave_axi4_mmio_0_arsize  := cpuIO.axiRa.ARSIZE
  chiplinkIO.slave_axi4_mmio_0_arburst := cpuIO.axiRa.ARBURST

  chiplinkIO.slave_axi4_mmio_0_rready := cpuIO.axiRd.RREADY

  cpuIO.axiWa.AWREADY := chiplinkIO.slave_axi4_mmio_0_awready
  cpuIO.axiWd.WREADY  := chiplinkIO.slave_axi4_mmio_0_wready
  cpuIO.axiWr.BVALID  := chiplinkIO.slave_axi4_mmio_0_bvalid
  cpuIO.axiWr.BID     := chiplinkIO.slave_axi4_mmio_0_bid
  cpuIO.axiWr.BRESP   := chiplinkIO.slave_axi4_mmio_0_bresp
  cpuIO.axiRa.ARREADY := chiplinkIO.slave_axi4_mmio_0_arready
  cpuIO.axiRd.RVALID  := chiplinkIO.slave_axi4_mmio_0_rvalid
  cpuIO.axiRd.RID     := chiplinkIO.slave_axi4_mmio_0_rid
  cpuIO.axiRd.RDATA   := chiplinkIO.slave_axi4_mmio_0_rdata
  cpuIO.axiRd.RRESP   := chiplinkIO.slave_axi4_mmio_0_rresp
  cpuIO.axiRd.RLAST   := chiplinkIO.slave_axi4_mmio_0_rlast

  cpuIO.axiWr.BUSER := DontCare
  cpuIO.axiRd.RUSER := DontCare
}

private object LinkMMIO {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel): LinkMMIO = new LinkMMIO(chiplinkIO, cpuIO)
}

private class LinkDMA(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel) {
  cpuIO := DontCare

  chiplinkIO.mem_axi4_0_awready := DontCare
  chiplinkIO.mem_axi4_0_wready  := DontCare
  chiplinkIO.mem_axi4_0_bvalid  := DontCare
  chiplinkIO.mem_axi4_0_bid     := DontCare
  chiplinkIO.mem_axi4_0_bresp   := DontCare
  chiplinkIO.mem_axi4_0_arready := DontCare
  chiplinkIO.mem_axi4_0_rvalid  := DontCare
  chiplinkIO.mem_axi4_0_rid     := DontCare
  chiplinkIO.mem_axi4_0_rdata   := DontCare
  chiplinkIO.mem_axi4_0_rresp   := DontCare
  chiplinkIO.mem_axi4_0_rlast   := DontCare
}

private object LinkDMA {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel): LinkDMA = new LinkDMA(chiplinkIO, cpuIO)
}

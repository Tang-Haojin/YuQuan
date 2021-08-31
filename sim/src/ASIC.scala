package sim

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import _root_.cpu._
import tools._
import _root_.peripheral.spi._
import _root_.peripheral.chiplink._
import _root_.peripheral.uart16550._
import _root_.peripheral._
import peripheral._
import _root_.cpu.component.AXISelect
import utils._

class ASIC(implicit val p: Parameters) extends Module with SimParams {
  val io = IO(new Bundle {
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

  val select = Module(new AXISelect)
  select.io.input  <> cpu.io.memAXI
  router.io.input  <> select.io.MMIO
  router.io.UartIO <> uartCtrl.io.channel
  router.io.PLICIO <> plic.io.channel
  router.io.SpiIO  <> spiCtrl.io.axi_s.channel
  LinkMEM (chiplink.io, select.io.RamIO)
  LinkMMIO(chiplink.io, router.io.ChiplinkIO)
  LinkDMA (chiplink.io, cpu.io.dmaAXI)

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

  plic.io.inter     := VecInit(Seq.fill(plic.io.inter.length)(0.B))
  plic.io.inter(10) := uartCtrl.io.interrupt
  cpu.io.intr       := plic.io.eip

  val delayCounter  = RegInit(15.U(4.W))
  val delayedResetn = (delayCounter === 0.U)

  when(delayCounter =/= 0.U) { delayCounter := delayCounter - 1.U }

  router.io.basic.ACLK           := clock
  router.io.basic.ARESETn        := !reset.asBool
  uartCtrl.io.basic.ACLK         := clock
  uartCtrl.io.basic.ARESETn      := !reset.asBool
  spiCtrl.io.axi_s.basic.ACLK    := clock
  spiCtrl.io.axi_s.basic.ARESETn := !reset.asBool
  plic.io.basic.ACLK             := clock
  plic.io.basic.ARESETn          := !reset.asBool
  chiplink.io.clock              := clock.asBool
  chiplink.io.reset              := reset

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

  cpuIO.axiWr.BUSER := 0.U
  cpuIO.axiRd.RUSER := 0.U
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

  cpuIO.axiWr.BUSER := 0.U
  cpuIO.axiRd.RUSER := 0.U
}

private object LinkMMIO {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel): LinkMMIO = new LinkMMIO(chiplinkIO, cpuIO)
}

private class LinkDMA(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel) {
  cpuIO.axiWa.AWADDR   := chiplinkIO.mem_axi4_0_awaddr
  cpuIO.axiWa.AWBURST  := chiplinkIO.mem_axi4_0_awburst
  cpuIO.axiWa.AWCACHE  := 0.U
  cpuIO.axiWa.AWID     := chiplinkIO.mem_axi4_0_awid
  cpuIO.axiWa.AWLEN    := chiplinkIO.mem_axi4_0_awlen
  cpuIO.axiWa.AWLOCK   := 0.U
  cpuIO.axiWa.AWPROT   := 0.U
  cpuIO.axiWa.AWQOS    := 0.U
  cpuIO.axiWa.AWREGION := 0.U
  cpuIO.axiWa.AWSIZE   := chiplinkIO.mem_axi4_0_awsize
  cpuIO.axiWa.AWUSER   := 0.U
  cpuIO.axiWa.AWVALID  := chiplinkIO.mem_axi4_0_awvalid
  cpuIO.axiWd.WDATA    := chiplinkIO.mem_axi4_0_wdata
  cpuIO.axiWd.WLAST    := chiplinkIO.mem_axi4_0_wlast
  cpuIO.axiWd.WSTRB    := chiplinkIO.mem_axi4_0_wstrb
  cpuIO.axiWd.WUSER    := 0.U
  cpuIO.axiWd.WVALID   := chiplinkIO.mem_axi4_0_wvalid
  cpuIO.axiWr.BREADY   := chiplinkIO.mem_axi4_0_bready
  cpuIO.axiRa.ARADDR   := chiplinkIO.mem_axi4_0_araddr
  cpuIO.axiRa.ARBURST  := chiplinkIO.mem_axi4_0_arburst
  cpuIO.axiRa.ARCACHE  := 0.U
  cpuIO.axiRa.ARID     := chiplinkIO.mem_axi4_0_arid
  cpuIO.axiRa.ARLEN    := chiplinkIO.mem_axi4_0_arlen
  cpuIO.axiRa.ARLOCK   := 0.U
  cpuIO.axiRa.ARPROT   := 0.U
  cpuIO.axiRa.ARQOS    := 0.U
  cpuIO.axiRa.ARREGION := 0.U
  cpuIO.axiRa.ARSIZE   := chiplinkIO.mem_axi4_0_arsize
  cpuIO.axiRa.ARUSER   := 0.U
  cpuIO.axiRa.ARVALID  := chiplinkIO.mem_axi4_0_arvalid
  cpuIO.axiRd.RREADY   := chiplinkIO.mem_axi4_0_rready

  chiplinkIO.mem_axi4_0_awready := cpuIO.axiWa.AWREADY
  chiplinkIO.mem_axi4_0_wready  := cpuIO.axiWd.WREADY
  chiplinkIO.mem_axi4_0_bvalid  := cpuIO.axiWr.BVALID
  chiplinkIO.mem_axi4_0_bid     := cpuIO.axiWr.BID
  chiplinkIO.mem_axi4_0_bresp   := cpuIO.axiWr.BRESP
  chiplinkIO.mem_axi4_0_arready := cpuIO.axiRa.ARREADY
  chiplinkIO.mem_axi4_0_rvalid  := cpuIO.axiRd.RVALID
  chiplinkIO.mem_axi4_0_rid     := cpuIO.axiRd.RID
  chiplinkIO.mem_axi4_0_rdata   := cpuIO.axiRd.RDATA
  chiplinkIO.mem_axi4_0_rresp   := cpuIO.axiRd.RRESP
  chiplinkIO.mem_axi4_0_rlast   := cpuIO.axiRd.RLAST
}

private object LinkDMA {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AxiMasterChannel): LinkDMA = new LinkDMA(chiplinkIO, cpuIO)
}

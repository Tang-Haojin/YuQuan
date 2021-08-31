package sim.cpu

import chisel3._
import chipsalliance.rocketchip.config._

import utils._

import peripheral._
import chiplink._

import sim._
import peripheral.ram._
import peripheral.uart._
import peripheral.spiFlash._
import peripheral.dmac._
import peripheral.storage._

class TestTop_ChipLink(io: DEBUG, clock: Clock, reset: Reset)(implicit p: Parameters) {
  val asic      = Module(new ASIC)
  val mem       = Module(new RAM)
  val chiplink  = Module(new ChiplinkTop)
  val tty       = Module(new TTY)
  val flash     = Module(new spiFlash)
  val storage   = Module(new Storage)
  val dmac      = Module(new DMAC)

  io <> asic.io.debug

  tty.io.srx         := asic.io.UartIO.stx
  asic.io.UartIO.srx := tty.io.stx

  flash.io <> asic.io.SpiIO

  dmac.io.toDevice <> storage.io.channel
  LinkMEM (chiplink.io, mem.io.channel)
  LinkMMIO(chiplink.io, dmac.io.fromCPU.channel)
  LinkDMA (chiplink.io, dmac.io.toCPU)

  chiplink.io.fpga_io_b2c_clk  := asic.io.ChiplinkIO.c2b.clk
  chiplink.io.fpga_io_b2c_data := asic.io.ChiplinkIO.c2b.data
  chiplink.io.fpga_io_b2c_rst  := asic.io.ChiplinkIO.c2b.rst
  chiplink.io.fpga_io_b2c_send := asic.io.ChiplinkIO.c2b.send
  asic.io.ChiplinkIO.b2c.clk   := chiplink.io.fpga_io_c2b_clk  
  asic.io.ChiplinkIO.b2c.data  := chiplink.io.fpga_io_c2b_data 
  asic.io.ChiplinkIO.b2c.rst   := chiplink.io.fpga_io_c2b_rst  
  asic.io.ChiplinkIO.b2c.send  := chiplink.io.fpga_io_c2b_send 

  mem.io.basic.ACLK             := clock
  mem.io.basic.ARESETn          := !reset.asBool
  chiplink.io.clock             := clock.asBool
  chiplink.io.reset             := reset
  storage.io.basic.ACLK         := clock
  storage.io.basic.ARESETn      := !reset.asBool
  dmac.io.fromCPU.basic.ACLK    := clock
  dmac.io.fromCPU.basic.ARESETn := !reset.asBool
}

private class LinkMEM(chiplinkIO: ChiplinkTopIO, memIO: AxiMasterChannel) {
  memIO.axiWa.AWVALID := chiplinkIO.mem_axi4_0_awvalid
  memIO.axiWa.AWID    := chiplinkIO.mem_axi4_0_awid
  memIO.axiWa.AWADDR  := chiplinkIO.mem_axi4_0_awaddr
  memIO.axiWa.AWLEN   := chiplinkIO.mem_axi4_0_awlen
  memIO.axiWa.AWSIZE  := chiplinkIO.mem_axi4_0_awsize
  memIO.axiWa.AWBURST := chiplinkIO.mem_axi4_0_awburst
  memIO.axiWa.AWLOCK  := chiplinkIO.mem_axi4_0_awlock
  memIO.axiWa.AWCACHE := chiplinkIO.mem_axi4_0_awcache
  memIO.axiWa.AWPROT  := chiplinkIO.mem_axi4_0_awprot
  memIO.axiWa.AWQOS   := chiplinkIO.mem_axi4_0_awqos

  memIO.axiWd.WVALID := chiplinkIO.mem_axi4_0_wvalid
  memIO.axiWd.WDATA  := chiplinkIO.mem_axi4_0_wdata
  memIO.axiWd.WSTRB  := chiplinkIO.mem_axi4_0_wstrb
  memIO.axiWd.WLAST  := chiplinkIO.mem_axi4_0_wlast

  memIO.axiWr.BREADY := chiplinkIO.mem_axi4_0_bready

  memIO.axiRa.ARVALID := chiplinkIO.mem_axi4_0_arvalid
  memIO.axiRa.ARID    := chiplinkIO.mem_axi4_0_arid
  memIO.axiRa.ARADDR  := chiplinkIO.mem_axi4_0_araddr
  memIO.axiRa.ARLEN   := chiplinkIO.mem_axi4_0_arlen
  memIO.axiRa.ARSIZE  := chiplinkIO.mem_axi4_0_arsize
  memIO.axiRa.ARBURST := chiplinkIO.mem_axi4_0_arburst
  memIO.axiRa.ARLOCK  := chiplinkIO.mem_axi4_0_arlock
  memIO.axiRa.ARCACHE := chiplinkIO.mem_axi4_0_arcache
  memIO.axiRa.ARPROT  := chiplinkIO.mem_axi4_0_arprot
  memIO.axiRa.ARQOS   := chiplinkIO.mem_axi4_0_arqos

  memIO.axiRd.RREADY := chiplinkIO.mem_axi4_0_rready

  chiplinkIO.mem_axi4_0_awready := memIO.axiWa.AWREADY
  chiplinkIO.mem_axi4_0_wready  := memIO.axiWd.WREADY
  chiplinkIO.mem_axi4_0_bvalid  := memIO.axiWr.BVALID
  chiplinkIO.mem_axi4_0_bid     := memIO.axiWr.BID
  chiplinkIO.mem_axi4_0_bresp   := memIO.axiWr.BRESP
  chiplinkIO.mem_axi4_0_arready := memIO.axiRa.ARREADY
  chiplinkIO.mem_axi4_0_rvalid  := memIO.axiRd.RVALID
  chiplinkIO.mem_axi4_0_rid     := memIO.axiRd.RID
  chiplinkIO.mem_axi4_0_rdata   := memIO.axiRd.RDATA
  chiplinkIO.mem_axi4_0_rresp   := memIO.axiRd.RRESP
  chiplinkIO.mem_axi4_0_rlast   := memIO.axiRd.RLAST

  memIO.axiWd.WUSER    := 0.U
  memIO.axiRa.ARREGION := 0.U
  memIO.axiRa.ARUSER   := 0.U
  memIO.axiWa.AWREGION := 0.U
  memIO.axiWa.AWUSER   := 0.U
}

private object LinkMEM {
  def apply(chiplinkIO: ChiplinkTopIO, memIO: AxiMasterChannel): LinkMEM = new LinkMEM(chiplinkIO, memIO)
}

private class LinkMMIO(chiplinkIO: ChiplinkTopIO, mmioIO: AxiMasterChannel) {
  mmioIO.axiWa.AWVALID  := chiplinkIO.mmio_axi4_0_awvalid
  mmioIO.axiWa.AWID     := chiplinkIO.mmio_axi4_0_awid
  mmioIO.axiWa.AWADDR   := chiplinkIO.mmio_axi4_0_awaddr
  mmioIO.axiWa.AWLEN    := chiplinkIO.mmio_axi4_0_awlen
  mmioIO.axiWa.AWSIZE   := chiplinkIO.mmio_axi4_0_awsize
  mmioIO.axiWa.AWBURST  := chiplinkIO.mmio_axi4_0_awburst
  mmioIO.axiWa.AWLOCK   := chiplinkIO.mmio_axi4_0_awlock
  mmioIO.axiWa.AWCACHE  := chiplinkIO.mmio_axi4_0_awcache
  mmioIO.axiWa.AWPROT   := chiplinkIO.mmio_axi4_0_awprot
  mmioIO.axiWa.AWQOS    := chiplinkIO.mmio_axi4_0_awqos
  mmioIO.axiWa.AWUSER   := 0.U
  mmioIO.axiWa.AWREGION := 0.U

  mmioIO.axiWd.WVALID := chiplinkIO.mmio_axi4_0_wvalid
  mmioIO.axiWd.WDATA  := chiplinkIO.mmio_axi4_0_wdata
  mmioIO.axiWd.WSTRB  := chiplinkIO.mmio_axi4_0_wstrb
  mmioIO.axiWd.WLAST  := chiplinkIO.mmio_axi4_0_wlast
  mmioIO.axiWd.WUSER  := 0.U

  mmioIO.axiWr.BREADY := chiplinkIO.mmio_axi4_0_bready

  mmioIO.axiRa.ARVALID  := chiplinkIO.mmio_axi4_0_arvalid
  mmioIO.axiRa.ARID     := chiplinkIO.mmio_axi4_0_arid
  mmioIO.axiRa.ARADDR   := chiplinkIO.mmio_axi4_0_araddr
  mmioIO.axiRa.ARLEN    := chiplinkIO.mmio_axi4_0_arlen
  mmioIO.axiRa.ARSIZE   := chiplinkIO.mmio_axi4_0_arsize
  mmioIO.axiRa.ARBURST  := chiplinkIO.mmio_axi4_0_arburst
  mmioIO.axiRa.ARLOCK   := chiplinkIO.mmio_axi4_0_arlock
  mmioIO.axiRa.ARCACHE  := chiplinkIO.mmio_axi4_0_arcache
  mmioIO.axiRa.ARPROT   := chiplinkIO.mmio_axi4_0_arprot
  mmioIO.axiRa.ARQOS    := chiplinkIO.mmio_axi4_0_arqos
  mmioIO.axiRa.ARREGION := 0.U
  mmioIO.axiRa.ARUSER   := 0.U

  mmioIO.axiRd.RREADY := chiplinkIO.mmio_axi4_0_rready

  chiplinkIO.mmio_axi4_0_awready := mmioIO.axiWa.AWREADY
  chiplinkIO.mmio_axi4_0_wready  := mmioIO.axiWd.WREADY
  chiplinkIO.mmio_axi4_0_bvalid  := mmioIO.axiWr.BVALID
  // chiplinkIO.mmio_axi4_0_bid     := mmioIO.axiWr.BID
  chiplinkIO.mmio_axi4_0_bresp   := mmioIO.axiWr.BRESP
  chiplinkIO.mmio_axi4_0_arready := mmioIO.axiRa.ARREADY
  chiplinkIO.mmio_axi4_0_rvalid  := mmioIO.axiRd.RVALID
  // chiplinkIO.mmio_axi4_0_rid     := mmioIO.axiRd.RID
  chiplinkIO.mmio_axi4_0_rdata   := mmioIO.axiRd.RDATA
  chiplinkIO.mmio_axi4_0_rresp   := mmioIO.axiRd.RRESP
  chiplinkIO.mmio_axi4_0_rlast   := mmioIO.axiRd.RLAST
}

private object LinkMMIO {
  def apply(chiplinkIO: ChiplinkTopIO, mmioIO: AxiMasterChannel): LinkMMIO = new LinkMMIO(chiplinkIO, mmioIO)
}

private class LinkDMA(chiplinkIO: ChiplinkTopIO, dmaIO: AxiMasterChannel) {
  chiplinkIO.l2_frontend_bus_axi4_0_awvalid := dmaIO.axiWa.AWVALID
  chiplinkIO.l2_frontend_bus_axi4_0_awid    := dmaIO.axiWa.AWID
  chiplinkIO.l2_frontend_bus_axi4_0_awaddr  := dmaIO.axiWa.AWADDR
  chiplinkIO.l2_frontend_bus_axi4_0_awlen   := dmaIO.axiWa.AWLEN
  chiplinkIO.l2_frontend_bus_axi4_0_awsize  := dmaIO.axiWa.AWSIZE
  chiplinkIO.l2_frontend_bus_axi4_0_awburst := dmaIO.axiWa.AWBURST
  chiplinkIO.l2_frontend_bus_axi4_0_wvalid  := dmaIO.axiWd.WVALID
  chiplinkIO.l2_frontend_bus_axi4_0_wdata   := dmaIO.axiWd.WDATA
  chiplinkIO.l2_frontend_bus_axi4_0_wstrb   := dmaIO.axiWd.WSTRB
  chiplinkIO.l2_frontend_bus_axi4_0_wlast   := dmaIO.axiWd.WLAST
  chiplinkIO.l2_frontend_bus_axi4_0_bready  := dmaIO.axiWr.BREADY
  chiplinkIO.l2_frontend_bus_axi4_0_arvalid := dmaIO.axiRa.ARVALID
  chiplinkIO.l2_frontend_bus_axi4_0_arid    := dmaIO.axiRa.ARID
  chiplinkIO.l2_frontend_bus_axi4_0_araddr  := dmaIO.axiRa.ARADDR
  chiplinkIO.l2_frontend_bus_axi4_0_arlen   := dmaIO.axiRa.ARLEN
  chiplinkIO.l2_frontend_bus_axi4_0_arsize  := dmaIO.axiRa.ARSIZE
  chiplinkIO.l2_frontend_bus_axi4_0_arburst := dmaIO.axiRa.ARBURST
  chiplinkIO.l2_frontend_bus_axi4_0_rready  := dmaIO.axiRd.RREADY

  dmaIO.axiWa.AWREADY := chiplinkIO.l2_frontend_bus_axi4_0_awready
  dmaIO.axiWd.WREADY  := chiplinkIO.l2_frontend_bus_axi4_0_wready
  dmaIO.axiWr.BID     := chiplinkIO.l2_frontend_bus_axi4_0_bid
  dmaIO.axiWr.BRESP   := chiplinkIO.l2_frontend_bus_axi4_0_bresp
  dmaIO.axiWr.BUSER   := 0.U
  dmaIO.axiWr.BVALID  := chiplinkIO.l2_frontend_bus_axi4_0_bvalid
  dmaIO.axiRa.ARREADY := chiplinkIO.l2_frontend_bus_axi4_0_arready
  dmaIO.axiRd.RDATA   := chiplinkIO.l2_frontend_bus_axi4_0_rdata
  dmaIO.axiRd.RID     := chiplinkIO.l2_frontend_bus_axi4_0_rid
  dmaIO.axiRd.RLAST   := chiplinkIO.l2_frontend_bus_axi4_0_rlast
  dmaIO.axiRd.RRESP   := chiplinkIO.l2_frontend_bus_axi4_0_rresp
  dmaIO.axiRd.RUSER   := 0.U
  dmaIO.axiRd.RVALID  := chiplinkIO.l2_frontend_bus_axi4_0_rvalid
}

private object LinkDMA {
  def apply(chiplinkIO: ChiplinkTopIO, dmaIO: AxiMasterChannel): LinkDMA = new LinkDMA(chiplinkIO, dmaIO)
}

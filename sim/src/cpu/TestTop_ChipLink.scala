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

private class LinkMEM(chiplinkIO: ChiplinkTopIO, memIO: AXI_BUNDLE) {
  memIO.aw.valid      := chiplinkIO.mem_axi4_0_awvalid
  memIO.aw.bits.id    := chiplinkIO.mem_axi4_0_awid
  memIO.aw.bits.addr  := chiplinkIO.mem_axi4_0_awaddr
  memIO.aw.bits.len   := chiplinkIO.mem_axi4_0_awlen
  memIO.aw.bits.size  := chiplinkIO.mem_axi4_0_awsize
  memIO.aw.bits.burst := chiplinkIO.mem_axi4_0_awburst
  memIO.aw.bits.lock  := chiplinkIO.mem_axi4_0_awlock
  memIO.aw.bits.cache := chiplinkIO.mem_axi4_0_awcache
  memIO.aw.bits.prot  := chiplinkIO.mem_axi4_0_awprot
  memIO.aw.bits.qos   := chiplinkIO.mem_axi4_0_awqos

  memIO.w.valid     := chiplinkIO.mem_axi4_0_wvalid
  memIO.w.bits.data := chiplinkIO.mem_axi4_0_wdata
  memIO.w.bits.strb := chiplinkIO.mem_axi4_0_wstrb
  memIO.w.bits.last := chiplinkIO.mem_axi4_0_wlast

  memIO.b.ready := chiplinkIO.mem_axi4_0_bready

  memIO.ar.valid      := chiplinkIO.mem_axi4_0_arvalid
  memIO.ar.bits.id    := chiplinkIO.mem_axi4_0_arid
  memIO.ar.bits.addr  := chiplinkIO.mem_axi4_0_araddr
  memIO.ar.bits.len   := chiplinkIO.mem_axi4_0_arlen
  memIO.ar.bits.size  := chiplinkIO.mem_axi4_0_arsize
  memIO.ar.bits.burst := chiplinkIO.mem_axi4_0_arburst
  memIO.ar.bits.lock  := chiplinkIO.mem_axi4_0_arlock
  memIO.ar.bits.cache := chiplinkIO.mem_axi4_0_arcache
  memIO.ar.bits.prot  := chiplinkIO.mem_axi4_0_arprot
  memIO.ar.bits.qos   := chiplinkIO.mem_axi4_0_arqos

  memIO.r.ready := chiplinkIO.mem_axi4_0_rready

  chiplinkIO.mem_axi4_0_awready := memIO.aw.ready
  chiplinkIO.mem_axi4_0_wready  := memIO.w.ready
  chiplinkIO.mem_axi4_0_bvalid  := memIO.b.valid
  chiplinkIO.mem_axi4_0_bid     := memIO.b.bits.id
  chiplinkIO.mem_axi4_0_bresp   := memIO.b.bits.resp
  chiplinkIO.mem_axi4_0_arready := memIO.ar.ready
  chiplinkIO.mem_axi4_0_rvalid  := memIO.r.valid
  chiplinkIO.mem_axi4_0_rid     := memIO.r.bits.id
  chiplinkIO.mem_axi4_0_rdata   := memIO.r.bits.data
  chiplinkIO.mem_axi4_0_rresp   := memIO.r.bits.resp
  chiplinkIO.mem_axi4_0_rlast   := memIO.r.bits.last

  memIO.w.bits.user    := 0.U
  memIO.ar.bits.region := 0.U
  memIO.ar.bits.user   := 0.U
  memIO.aw.bits.region := 0.U
  memIO.aw.bits.user   := 0.U
}

private object LinkMEM {
  def apply(chiplinkIO: ChiplinkTopIO, memIO: AXI_BUNDLE): LinkMEM = new LinkMEM(chiplinkIO, memIO)
}

private class LinkMMIO(chiplinkIO: ChiplinkTopIO, mmioIO: AXI_BUNDLE) {
  mmioIO.aw.valid       := chiplinkIO.mmio_axi4_0_awvalid
  mmioIO.aw.bits.id     := chiplinkIO.mmio_axi4_0_awid
  mmioIO.aw.bits.addr   := chiplinkIO.mmio_axi4_0_awaddr
  mmioIO.aw.bits.len    := chiplinkIO.mmio_axi4_0_awlen
  mmioIO.aw.bits.size   := chiplinkIO.mmio_axi4_0_awsize
  mmioIO.aw.bits.burst  := chiplinkIO.mmio_axi4_0_awburst
  mmioIO.aw.bits.lock   := chiplinkIO.mmio_axi4_0_awlock
  mmioIO.aw.bits.cache  := chiplinkIO.mmio_axi4_0_awcache
  mmioIO.aw.bits.prot   := chiplinkIO.mmio_axi4_0_awprot
  mmioIO.aw.bits.qos    := chiplinkIO.mmio_axi4_0_awqos
  mmioIO.aw.bits.user   := 0.U
  mmioIO.aw.bits.region := 0.U

  mmioIO.w.valid     := chiplinkIO.mmio_axi4_0_wvalid
  mmioIO.w.bits.data := chiplinkIO.mmio_axi4_0_wdata
  mmioIO.w.bits.strb := chiplinkIO.mmio_axi4_0_wstrb
  mmioIO.w.bits.last := chiplinkIO.mmio_axi4_0_wlast
  mmioIO.w.bits.user := 0.U

  mmioIO.b.ready := chiplinkIO.mmio_axi4_0_bready

  mmioIO.ar.valid       := chiplinkIO.mmio_axi4_0_arvalid
  mmioIO.ar.bits.id     := chiplinkIO.mmio_axi4_0_arid
  mmioIO.ar.bits.addr   := chiplinkIO.mmio_axi4_0_araddr
  mmioIO.ar.bits.len    := chiplinkIO.mmio_axi4_0_arlen
  mmioIO.ar.bits.size   := chiplinkIO.mmio_axi4_0_arsize
  mmioIO.ar.bits.burst  := chiplinkIO.mmio_axi4_0_arburst
  mmioIO.ar.bits.lock   := chiplinkIO.mmio_axi4_0_arlock
  mmioIO.ar.bits.cache  := chiplinkIO.mmio_axi4_0_arcache
  mmioIO.ar.bits.prot   := chiplinkIO.mmio_axi4_0_arprot
  mmioIO.ar.bits.qos    := chiplinkIO.mmio_axi4_0_arqos
  mmioIO.ar.bits.region := 0.U
  mmioIO.ar.bits.user   := 0.U

  mmioIO.r.ready := chiplinkIO.mmio_axi4_0_rready

  chiplinkIO.mmio_axi4_0_awready := mmioIO.aw.ready
  chiplinkIO.mmio_axi4_0_wready  := mmioIO.w.ready
  chiplinkIO.mmio_axi4_0_bvalid  := mmioIO.b.valid
  // chiplinkIO.mmio_axi4_0_bid     := mmioIO.b.bits.id
  chiplinkIO.mmio_axi4_0_bresp   := mmioIO.b.bits.resp
  chiplinkIO.mmio_axi4_0_arready := mmioIO.ar.ready
  chiplinkIO.mmio_axi4_0_rvalid  := mmioIO.r.valid
  // chiplinkIO.mmio_axi4_0_rid     := mmioIO.r.bits.id
  chiplinkIO.mmio_axi4_0_rdata   := mmioIO.r.bits.data
  chiplinkIO.mmio_axi4_0_rresp   := mmioIO.r.bits.resp
  chiplinkIO.mmio_axi4_0_rlast   := mmioIO.r.bits.last
}

private object LinkMMIO {
  def apply(chiplinkIO: ChiplinkTopIO, mmioIO: AXI_BUNDLE): LinkMMIO = new LinkMMIO(chiplinkIO, mmioIO)
}

private class LinkDMA(chiplinkIO: ChiplinkTopIO, dmaIO: AXI_BUNDLE) {
  chiplinkIO.l2_frontend_bus_axi4_0_awvalid := dmaIO.aw.valid
  chiplinkIO.l2_frontend_bus_axi4_0_awid    := dmaIO.aw.bits.id
  chiplinkIO.l2_frontend_bus_axi4_0_awaddr  := dmaIO.aw.bits.addr
  chiplinkIO.l2_frontend_bus_axi4_0_awlen   := dmaIO.aw.bits.len
  chiplinkIO.l2_frontend_bus_axi4_0_awsize  := dmaIO.aw.bits.size
  chiplinkIO.l2_frontend_bus_axi4_0_awburst := dmaIO.aw.bits.burst
  chiplinkIO.l2_frontend_bus_axi4_0_wvalid  := dmaIO.w.valid
  chiplinkIO.l2_frontend_bus_axi4_0_wdata   := dmaIO.w.bits.data
  chiplinkIO.l2_frontend_bus_axi4_0_wstrb   := dmaIO.w.bits.strb
  chiplinkIO.l2_frontend_bus_axi4_0_wlast   := dmaIO.w.bits.last
  chiplinkIO.l2_frontend_bus_axi4_0_bready  := dmaIO.b.ready
  chiplinkIO.l2_frontend_bus_axi4_0_arvalid := dmaIO.ar.valid
  chiplinkIO.l2_frontend_bus_axi4_0_arid    := dmaIO.ar.bits.id
  chiplinkIO.l2_frontend_bus_axi4_0_araddr  := dmaIO.ar.bits.addr
  chiplinkIO.l2_frontend_bus_axi4_0_arlen   := dmaIO.ar.bits.len
  chiplinkIO.l2_frontend_bus_axi4_0_arsize  := dmaIO.ar.bits.size
  chiplinkIO.l2_frontend_bus_axi4_0_arburst := dmaIO.ar.bits.burst
  chiplinkIO.l2_frontend_bus_axi4_0_rready  := dmaIO.r.ready

  dmaIO.aw.ready    := chiplinkIO.l2_frontend_bus_axi4_0_awready
  dmaIO.w.ready     := chiplinkIO.l2_frontend_bus_axi4_0_wready
  dmaIO.b.bits.id   := chiplinkIO.l2_frontend_bus_axi4_0_bid
  dmaIO.b.bits.resp := chiplinkIO.l2_frontend_bus_axi4_0_bresp
  dmaIO.b.bits.user := 0.U
  dmaIO.b.valid     := chiplinkIO.l2_frontend_bus_axi4_0_bvalid
  dmaIO.ar.ready    := chiplinkIO.l2_frontend_bus_axi4_0_arready
  dmaIO.r.bits.data := chiplinkIO.l2_frontend_bus_axi4_0_rdata
  dmaIO.r.bits.id   := chiplinkIO.l2_frontend_bus_axi4_0_rid
  dmaIO.r.bits.last := chiplinkIO.l2_frontend_bus_axi4_0_rlast
  dmaIO.r.bits.resp := chiplinkIO.l2_frontend_bus_axi4_0_rresp
  dmaIO.r.bits.user := 0.U
  dmaIO.r.valid     := chiplinkIO.l2_frontend_bus_axi4_0_rvalid
}

private object LinkDMA {
  def apply(chiplinkIO: ChiplinkTopIO, dmaIO: AXI_BUNDLE): LinkDMA = new LinkDMA(chiplinkIO, dmaIO)
}

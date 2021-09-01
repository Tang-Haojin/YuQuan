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
  select.io.input  <> cpu.io.master
  router.io.input  <> select.io.MMIO
  router.io.UartIO <> uartCtrl.io.channel
  router.io.PLICIO <> plic.io.channel
  router.io.SpiIO  <> spiCtrl.io.axi_s.channel
  LinkMEM (chiplink.io, select.io.RamIO)
  LinkMMIO(chiplink.io, router.io.ChiplinkIO)
  LinkDMA (chiplink.io, cpu.io.slave)

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
  cpu.io.interrupt       := plic.io.eip

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

private class LinkMEM(chiplinkIO: ChiplinkBridgeIO, cpuIO: AXI_BUNDLE) {
  chiplinkIO.slave_axi4_mem_0_awvalid := cpuIO.aw.valid
  chiplinkIO.slave_axi4_mem_0_awid    := cpuIO.aw.bits.id
  chiplinkIO.slave_axi4_mem_0_awaddr  := cpuIO.aw.bits.addr
  chiplinkIO.slave_axi4_mem_0_awlen   := cpuIO.aw.bits.len
  chiplinkIO.slave_axi4_mem_0_awsize  := cpuIO.aw.bits.size
  chiplinkIO.slave_axi4_mem_0_awburst := cpuIO.aw.bits.burst

  chiplinkIO.slave_axi4_mem_0_wvalid := cpuIO.w.valid
  chiplinkIO.slave_axi4_mem_0_wdata  := cpuIO.w.bits.data
  chiplinkIO.slave_axi4_mem_0_wstrb  := cpuIO.w.bits.strb
  chiplinkIO.slave_axi4_mem_0_wlast  := cpuIO.w.bits.last

  chiplinkIO.slave_axi4_mem_0_bready := cpuIO.b.ready

  chiplinkIO.slave_axi4_mem_0_arvalid := cpuIO.ar.valid
  chiplinkIO.slave_axi4_mem_0_arid    := cpuIO.ar.bits.id
  chiplinkIO.slave_axi4_mem_0_araddr  := cpuIO.ar.bits.addr
  chiplinkIO.slave_axi4_mem_0_arlen   := cpuIO.ar.bits.len
  chiplinkIO.slave_axi4_mem_0_arsize  := cpuIO.ar.bits.size
  chiplinkIO.slave_axi4_mem_0_arburst := cpuIO.ar.bits.burst

  chiplinkIO.slave_axi4_mem_0_rready := cpuIO.r.ready

  cpuIO.aw.ready    := chiplinkIO.slave_axi4_mem_0_awready
  cpuIO.aw.ready    := chiplinkIO.slave_axi4_mem_0_wready
  cpuIO.b.valid     := chiplinkIO.slave_axi4_mem_0_bvalid
  cpuIO.b.bits.id   := chiplinkIO.slave_axi4_mem_0_bid
  cpuIO.b.bits.resp := chiplinkIO.slave_axi4_mem_0_bresp
  cpuIO.ar.ready    := chiplinkIO.slave_axi4_mem_0_arready
  cpuIO.r.valid     := chiplinkIO.slave_axi4_mem_0_rvalid
  cpuIO.r.bits.id   := chiplinkIO.slave_axi4_mem_0_rid
  cpuIO.r.bits.data := chiplinkIO.slave_axi4_mem_0_rdata
  cpuIO.r.bits.resp := chiplinkIO.slave_axi4_mem_0_rresp
  cpuIO.r.bits.last := chiplinkIO.slave_axi4_mem_0_rlast

  cpuIO.b.bits.user := 0.U
  cpuIO.r.bits.user := 0.U
}

private object LinkMEM {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AXI_BUNDLE): LinkMEM = new LinkMEM(chiplinkIO, cpuIO)
}

private class LinkMMIO(chiplinkIO: ChiplinkBridgeIO, cpuIO: AXI_BUNDLE) {
  chiplinkIO.slave_axi4_mmio_0_awvalid := cpuIO.aw.valid
  chiplinkIO.slave_axi4_mmio_0_awid    := cpuIO.aw.bits.id
  chiplinkIO.slave_axi4_mmio_0_awaddr  := cpuIO.aw.bits.addr
  chiplinkIO.slave_axi4_mmio_0_awlen   := cpuIO.aw.bits.len
  chiplinkIO.slave_axi4_mmio_0_awsize  := cpuIO.aw.bits.size
  chiplinkIO.slave_axi4_mmio_0_awburst := cpuIO.aw.bits.burst

  chiplinkIO.slave_axi4_mmio_0_wvalid := cpuIO.w.valid
  chiplinkIO.slave_axi4_mmio_0_wdata  := cpuIO.w.bits.data
  chiplinkIO.slave_axi4_mmio_0_wstrb  := cpuIO.w.bits.strb
  chiplinkIO.slave_axi4_mmio_0_wlast  := cpuIO.w.bits.last

  chiplinkIO.slave_axi4_mmio_0_bready := cpuIO.b.ready

  chiplinkIO.slave_axi4_mmio_0_arvalid := cpuIO.ar.valid
  chiplinkIO.slave_axi4_mmio_0_arid    := cpuIO.ar.bits.id
  chiplinkIO.slave_axi4_mmio_0_araddr  := cpuIO.ar.bits.addr
  chiplinkIO.slave_axi4_mmio_0_arlen   := cpuIO.ar.bits.len
  chiplinkIO.slave_axi4_mmio_0_arsize  := cpuIO.ar.bits.size
  chiplinkIO.slave_axi4_mmio_0_arburst := cpuIO.ar.bits.burst

  chiplinkIO.slave_axi4_mmio_0_rready := cpuIO.r.ready

  cpuIO.aw.ready    := chiplinkIO.slave_axi4_mmio_0_awready
  cpuIO.w.ready     := chiplinkIO.slave_axi4_mmio_0_wready
  cpuIO.b.valid     := chiplinkIO.slave_axi4_mmio_0_bvalid
  cpuIO.b.bits.id   := chiplinkIO.slave_axi4_mmio_0_bid
  cpuIO.b.bits.resp := chiplinkIO.slave_axi4_mmio_0_bresp
  cpuIO.ar.ready    := chiplinkIO.slave_axi4_mmio_0_arready
  cpuIO.r.valid     := chiplinkIO.slave_axi4_mmio_0_rvalid
  cpuIO.r.bits.id   := chiplinkIO.slave_axi4_mmio_0_rid
  cpuIO.r.bits.data := chiplinkIO.slave_axi4_mmio_0_rdata
  cpuIO.r.bits.resp := chiplinkIO.slave_axi4_mmio_0_rresp
  cpuIO.r.bits.last := chiplinkIO.slave_axi4_mmio_0_rlast

  cpuIO.b.bits.user := 0.U
  cpuIO.r.bits.user := 0.U
}

private object LinkMMIO {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AXI_BUNDLE): LinkMMIO = new LinkMMIO(chiplinkIO, cpuIO)
}

private class LinkDMA(chiplinkIO: ChiplinkBridgeIO, cpuIO: AXI_BUNDLE) {
  cpuIO.aw.bits.addr   := chiplinkIO.mem_axi4_0_awaddr
  cpuIO.aw.bits.burst  := chiplinkIO.mem_axi4_0_awburst
  cpuIO.aw.bits.cache  := 0.U
  cpuIO.aw.bits.id     := chiplinkIO.mem_axi4_0_awid
  cpuIO.aw.bits.len    := chiplinkIO.mem_axi4_0_awlen
  cpuIO.aw.bits.lock   := 0.U
  cpuIO.aw.bits.prot   := 0.U
  cpuIO.aw.bits.qos    := 0.U
  cpuIO.aw.bits.region := 0.U
  cpuIO.aw.bits.size   := chiplinkIO.mem_axi4_0_awsize
  cpuIO.aw.bits.user   := 0.U
  cpuIO.aw.valid       := chiplinkIO.mem_axi4_0_awvalid
  cpuIO.w.bits.data    := chiplinkIO.mem_axi4_0_wdata
  cpuIO.w.bits.last    := chiplinkIO.mem_axi4_0_wlast
  cpuIO.w.bits.strb    := chiplinkIO.mem_axi4_0_wstrb
  cpuIO.w.bits.user    := 0.U
  cpuIO.w.valid        := chiplinkIO.mem_axi4_0_wvalid
  cpuIO.b.ready        := chiplinkIO.mem_axi4_0_bready
  cpuIO.ar.bits.addr   := chiplinkIO.mem_axi4_0_araddr
  cpuIO.ar.bits.burst  := chiplinkIO.mem_axi4_0_arburst
  cpuIO.ar.bits.cache  := 0.U
  cpuIO.ar.bits.id     := chiplinkIO.mem_axi4_0_arid
  cpuIO.ar.bits.len    := chiplinkIO.mem_axi4_0_arlen
  cpuIO.ar.bits.lock   := 0.U
  cpuIO.ar.bits.prot   := 0.U
  cpuIO.ar.bits.qos    := 0.U
  cpuIO.ar.bits.region := 0.U
  cpuIO.ar.bits.size   := chiplinkIO.mem_axi4_0_arsize
  cpuIO.ar.bits.user   := 0.U
  cpuIO.ar.valid       := chiplinkIO.mem_axi4_0_arvalid
  cpuIO.r.ready        := chiplinkIO.mem_axi4_0_rready

  chiplinkIO.mem_axi4_0_awready := cpuIO.aw.ready
  chiplinkIO.mem_axi4_0_wready  := cpuIO.w.ready
  chiplinkIO.mem_axi4_0_bvalid  := cpuIO.b.valid
  chiplinkIO.mem_axi4_0_bid     := cpuIO.b.bits.id
  chiplinkIO.mem_axi4_0_bresp   := cpuIO.b.bits.resp
  chiplinkIO.mem_axi4_0_arready := cpuIO.ar.ready
  chiplinkIO.mem_axi4_0_rvalid  := cpuIO.r.valid
  chiplinkIO.mem_axi4_0_rid     := cpuIO.r.bits.id
  chiplinkIO.mem_axi4_0_rdata   := cpuIO.r.bits.data
  chiplinkIO.mem_axi4_0_rresp   := cpuIO.r.bits.resp
  chiplinkIO.mem_axi4_0_rlast   := cpuIO.r.bits.last
}

private object LinkDMA {
  def apply(chiplinkIO: ChiplinkBridgeIO, cpuIO: AXI_BUNDLE): LinkDMA = new LinkDMA(chiplinkIO, cpuIO)
}

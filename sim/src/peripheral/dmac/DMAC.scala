package sim.peripheral.dmac

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import sim._

class DMAC(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new Bundle {
    val toCPU    = new AxiMasterChannel
    val toDevice = new AxiMasterChannel
    val fromCPU  = new AxiSlaveIO
  })

  dontTouch(io)

  private val idle::busy::Nil = Enum(2)

  withClockAndReset(io.fromCPU.basic.ACLK, !io.fromCPU.basic.ARESETn) {
    val regDeviceAddr = RegInit(0.U(xlen.W))
    val regCpuAddr    = RegInit(0.U(xlen.W))
    val regTransLen   = RegInit(0.U(xlen.W))
    val regFree       = RegInit(1.B)

    val fifo = Module(new YQueue(UInt(xlen.W), 8))
    fifo.io.enq.valid := io.toDevice.axiRd.RREADY && io.toDevice.axiRd.RVALID
    fifo.io.enq.bits  := io.toDevice.axiRd.RDATA
    fifo.io.deq.ready := io.toCPU.axiWd.WREADY && io.toCPU.axiWd.WVALID

    val toDevice = new ToDevice(io.toDevice, fifo.io, regDeviceAddr)
    val toCPU    = new ToCPU(io.toCPU, toDevice, fifo.io, regFree, regCpuAddr)
    val fromCPU  = new FromCPU(io.fromCPU.channel, toDevice, toCPU, regDeviceAddr, regCpuAddr, regTransLen, regFree)
  }
}

private class FromCPU(fromCPU: AxiMasterChannel, toDevice: ToDevice, toCPU: ToCPU, devAddr: UInt, cpuAddr: UInt, transLen: UInt, free: Bool)(implicit val p: Parameters) extends SimParams {
  val AWREADY = RegInit(1.B); fromCPU.axiWa.AWREADY := AWREADY
  val WREADY  = RegInit(0.B); fromCPU.axiWd.WREADY  := WREADY
  val BVALID  = RegInit(0.B); fromCPU.axiWr.BVALID  := BVALID
  val ARREADY = RegInit(1.B); fromCPU.axiRa.ARREADY := ARREADY
  val RVALID  = RegInit(0.B); fromCPU.axiRd.RVALID  := RVALID

  val RID    = RegInit(0.U(idlen.W)); fromCPU.axiRd.RID := RID
  val BID    = RegInit(0.U(idlen.W)); fromCPU.axiWr.BID := BID
  val AWADDR = RegInit(0.U(alen.W))
  val RDATA  = RegInit(0.U(xlen.W)); fromCPU.axiRd.RDATA := RDATA

  val wireRawRData = WireDefault(0.U(32.W))
  val wireWData = fromCPU.axiWd.WDATA
  switch(fromCPU.axiRa.ARADDR) {
    is(DMAC.DEVICE_ADDR_REG.U) { wireRawRData := devAddr }
    is(DMAC.MEMORY_ADDR_REG.U) { wireRawRData := cpuAddr }
    is(DMAC.TRANS_LENTH_REG.U) { wireRawRData := transLen }
    is(DMAC.DMAC_STATUS_REG.U) { wireRawRData := 0.U((xlen - 1).W) ## free }
  }

  when(fromCPU.axiRd.RVALID && fromCPU.axiRd.RREADY) {
    RVALID  := 0.B
    ARREADY := 1.B
  }.elsewhen(fromCPU.axiRa.ARVALID && fromCPU.axiRa.ARREADY) {
    RDATA   := wireRawRData
    RID     := fromCPU.axiRa.ARID
    ARREADY := 0.B
    RVALID  := 1.B
  }

  when(fromCPU.axiWa.AWVALID && fromCPU.axiWa.AWREADY) {
    AWADDR  := fromCPU.axiWa.AWADDR
    BID     := fromCPU.axiWa.AWID
    AWREADY := 0.B
    WREADY  := 1.B
  }

  when(fromCPU.axiWd.WVALID && fromCPU.axiWd.WREADY) {
    switch(AWADDR) {
      is(DMAC.DEVICE_ADDR_REG.U) { devAddr  := wireWData }
      is(DMAC.MEMORY_ADDR_REG.U) { cpuAddr  := wireWData }
      is(DMAC.TRANS_LENTH_REG.U) { transLen := wireWData }
      is(DMAC.DMAC_STATUS_REG.U) {
        free := 0.B
        toDevice.len     := transLen(31, 3) - 1.U
        toCPU.len        := transLen(31, 3) - 1.U
        toDevice.ARVALID := 1.B
        toCPU.AWVALID    := 1.B
        toCPU.WVALID     := 1.B
      }
  }
    WREADY := 0.B
    BVALID := 1.B
  }

  when(fromCPU.axiWr.BVALID && fromCPU.axiWr.BREADY) {
    AWREADY := 1.B
    BVALID  := 0.B
  }

  fromCPU.axiRd.RLAST := 1.B
  fromCPU.axiRd.RRESP := 0.U
  fromCPU.axiRd.RUSER := 0.U
  fromCPU.axiWr.BRESP := 0.U
  fromCPU.axiWr.BUSER := 0.U
}

private class ToCPU(toCPU: AxiMasterChannel, toDevice: ToDevice, fifo: QueueIO[UInt], free: Bool, cpuAddr: UInt)(implicit val p: Parameters) extends SimParams {
  val AWVALID = RegInit(0.B); toCPU.axiWa.AWVALID := AWVALID
  val WVALID  = RegInit(0.B); toCPU.axiWd.WVALID  := WVALID && fifo.deq.valid
  val BREADY  = RegInit(0.B); toCPU.axiWr.BREADY  := BREADY

  val len = RegInit(0.U(32.W))
  val wireWLAST = WireDefault(0.B)

  when(toCPU.axiWa.AWREADY && toCPU.axiWa.AWVALID) {
    AWVALID := 0.B
    BREADY  := 1.B
  }

  when(toCPU.axiWd.WREADY && toCPU.axiWd.WVALID) {
    when(len === 0.U) {
      WVALID    := 0.B
      BREADY    := 1.B
      wireWLAST := 1.B
    }.otherwise { len := len - 1.U }
  }

  when(toCPU.axiWr.BREADY && toCPU.axiWr.BVALID) {
    BREADY := 0.B
    free   := 1.B
  }

  toCPU.axiWd.WLAST := wireWLAST
  toCPU.axiWd.WDATA := fifo.deq.bits
  toCPU.axiWd.WSTRB := Fill(xlen / 8, 1.B)
  toCPU.axiWd.WUSER := 0.U

  toCPU.axiWa.AWADDR   := cpuAddr
  toCPU.axiWa.AWBURST  := 1.U
  toCPU.axiWa.AWCACHE  := 0.U
  toCPU.axiWa.AWID     := 2.U
  toCPU.axiWa.AWLEN    := toDevice.len
  toCPU.axiWa.AWLOCK   := 0.U
  toCPU.axiWa.AWPROT   := 0.U
  toCPU.axiWa.AWQOS    := 0.U
  toCPU.axiWa.AWREGION := 0.U
  toCPU.axiWa.AWSIZE   := axSize.U
  toCPU.axiWa.AWUSER   := 0.U

  toCPU.axiRd.RREADY := 0.B

  toCPU.axiRa.ARADDR   := 0.U
  toCPU.axiRa.ARBURST  := 1.U
  toCPU.axiRa.ARCACHE  := 0.U
  toCPU.axiRa.ARID     := 0.U
  toCPU.axiRa.ARLEN    := 0.U
  toCPU.axiRa.ARLOCK   := 0.U
  toCPU.axiRa.ARPROT   := 0.U
  toCPU.axiRa.ARQOS    := 0.U
  toCPU.axiRa.ARREGION := 0.U
  toCPU.axiRa.ARSIZE   := 0.U
  toCPU.axiRa.ARUSER   := 0.U
  toCPU.axiRa.ARVALID  := 0.B
}

private class ToDevice(toDevice: AxiMasterChannel, fifo: QueueIO[UInt], devAddr: UInt)(implicit val p: Parameters) extends SimParams {
  val ARVALID = RegInit(0.B); toDevice.axiRa.ARVALID := ARVALID
  val RREADY  = RegInit(0.B); toDevice.axiRd.RREADY  := RREADY && fifo.enq.ready

  val len = RegInit(0.U(32.W))

  when(toDevice.axiRd.RREADY && toDevice.axiRd.RVALID) {
    when(toDevice.axiRd.RLAST) { RREADY := 0.B }
  }.elsewhen(toDevice.axiRa.ARREADY && toDevice.axiRa.ARVALID) {
    ARVALID := 0.B
    RREADY  := 1.B
  }

  toDevice.axiRa.ARADDR   := devAddr
  toDevice.axiRa.ARBURST  := 1.U
  toDevice.axiRa.ARCACHE  := 0.U
  toDevice.axiRa.ARID     := 2.U
  toDevice.axiRa.ARLEN    := len
  toDevice.axiRa.ARLOCK   := 0.U
  toDevice.axiRa.ARPROT   := 0.U
  toDevice.axiRa.ARQOS    := 0.U
  toDevice.axiRa.ARREGION := 0.U
  toDevice.axiRa.ARSIZE   := axSize.U
  toDevice.axiRa.ARUSER   := 0.U

  toDevice.axiWr.BREADY := 0.B

  toDevice.axiWd.WDATA  := 0.U
  toDevice.axiWd.WLAST  := 0.B
  toDevice.axiWd.WSTRB  := 0.U
  toDevice.axiWd.WUSER  := 0.U
  toDevice.axiWd.WVALID := 0.B

  toDevice.axiWa.AWADDR   := 0.U
  toDevice.axiWa.AWBURST  := 1.U
  toDevice.axiWa.AWCACHE  := 0.U
  toDevice.axiWa.AWID     := 0.U
  toDevice.axiWa.AWLEN    := 0.U
  toDevice.axiWa.AWLOCK   := 0.U
  toDevice.axiWa.AWPROT   := 0.U
  toDevice.axiWa.AWQOS    := 0.U
  toDevice.axiWa.AWREGION := 0.U
  toDevice.axiWa.AWSIZE   := 0.U
  toDevice.axiWa.AWUSER   := 0.U
  toDevice.axiWa.AWVALID  := 0.B
}

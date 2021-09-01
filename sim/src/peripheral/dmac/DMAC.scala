package sim.peripheral.dmac

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import sim._

class DMAC(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new Bundle {
    val toCPU    = new AXI_BUNDLE
    val toDevice = new AXI_BUNDLE
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
    fifo.io.enq.valid := io.toDevice.r.fire
    fifo.io.enq.bits  := io.toDevice.r.bits.data
    fifo.io.deq.ready := io.toCPU.w.fire

    val toDevice = new ToDevice(io.toDevice, fifo.io, regDeviceAddr)
    val toCPU    = new ToCPU(io.toCPU, toDevice, fifo.io, regFree, regCpuAddr)
    val fromCPU  = new FromCPU(io.fromCPU.channel, toDevice, toCPU, regDeviceAddr, regCpuAddr, regTransLen, regFree)
  }
}

private class FromCPU(fromCPU: AXI_BUNDLE, toDevice: ToDevice, toCPU: ToCPU, devAddr: UInt, cpuAddr: UInt, transLen: UInt, free: Bool)(implicit val p: Parameters) extends SimParams {
  val AWREADY = RegInit(1.B); fromCPU.aw.ready := AWREADY
  val WREADY  = RegInit(0.B); fromCPU.w .ready := WREADY
  val BVALID  = RegInit(0.B); fromCPU.b .valid := BVALID
  val ARREADY = RegInit(1.B); fromCPU.ar.ready := ARREADY
  val RVALID  = RegInit(0.B); fromCPU.r .valid := RVALID

  val RID    = RegInit(0.U(idlen.W)); fromCPU.r.bits.id := RID
  val BID    = RegInit(0.U(idlen.W)); fromCPU.b.bits.id := BID
  val AWADDR = RegInit(0.U(alen.W))
  val RDATA  = RegInit(0.U(xlen.W)); fromCPU.r.bits.data := RDATA

  val wireRawRData = WireDefault(0.U(32.W))
  val wireWData = fromCPU.w.bits.data
  switch(fromCPU.ar.bits.addr) {
    is(DMAC.DEVICE_ADDR_REG.U) { wireRawRData := devAddr }
    is(DMAC.MEMORY_ADDR_REG.U) { wireRawRData := cpuAddr }
    is(DMAC.TRANS_LENTH_REG.U) { wireRawRData := transLen }
    is(DMAC.DMAC_STATUS_REG.U) { wireRawRData := 0.U((xlen - 1).W) ## free }
  }

  when(fromCPU.r.fire) {
    RVALID  := 0.B
    ARREADY := 1.B
  }.elsewhen(fromCPU.ar.fire) {
    RDATA   := wireRawRData
    RID     := fromCPU.ar.bits.id
    ARREADY := 0.B
    RVALID  := 1.B
  }

  when(fromCPU.aw.fire) {
    AWADDR  := fromCPU.aw.bits.addr
    BID     := fromCPU.aw.bits.id
    AWREADY := 0.B
    WREADY  := 1.B
  }

  when(fromCPU.w.fire) {
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

  when(fromCPU.b.fire) {
    AWREADY := 1.B
    BVALID  := 0.B
  }

  fromCPU.r.bits.last := 1.B
  fromCPU.r.bits.resp := 0.U
  fromCPU.r.bits.user := 0.U
  fromCPU.b.bits.resp := 0.U
  fromCPU.b.bits.user := 0.U
}

private class ToCPU(toCPU: AXI_BUNDLE, toDevice: ToDevice, fifo: QueueIO[UInt], free: Bool, cpuAddr: UInt)(implicit val p: Parameters) extends SimParams {
  val AWVALID = RegInit(0.B); toCPU.aw.valid := AWVALID
  val WVALID  = RegInit(0.B); toCPU.w .valid := WVALID && fifo.deq.valid
  val BREADY  = RegInit(0.B); toCPU.b .ready := BREADY

  val len = RegInit(0.U(32.W))
  val wireWLAST = WireDefault(0.B)

  when(toCPU.aw.fire) {
    AWVALID := 0.B
    BREADY  := 1.B
  }

  when(toCPU.w.fire) {
    when(len === 0.U) {
      WVALID    := 0.B
      BREADY    := 1.B
      wireWLAST := 1.B
    }.otherwise { len := len - 1.U }
  }

  when(toCPU.b.fire) {
    BREADY := 0.B
    free   := 1.B
  }

  toCPU.w.bits.last := wireWLAST
  toCPU.w.bits.data := fifo.deq.bits
  toCPU.w.bits.strb := Fill(xlen / 8, 1.B)
  toCPU.w.bits.user := 0.U

  toCPU.aw.bits.addr   := cpuAddr
  toCPU.aw.bits.burst  := 1.U
  toCPU.aw.bits.cache  := 0.U
  toCPU.aw.bits.id     := 2.U
  toCPU.aw.bits.len    := toDevice.len
  toCPU.aw.bits.lock   := 0.U
  toCPU.aw.bits.prot   := 0.U
  toCPU.aw.bits.qos    := 0.U
  toCPU.aw.bits.region := 0.U
  toCPU.aw.bits.size   := axSize.U
  toCPU.aw.bits.user   := 0.U

  toCPU.r.ready := 0.B

  toCPU.ar.bits.addr   := 0.U
  toCPU.ar.bits.burst  := 1.U
  toCPU.ar.bits.cache  := 0.U
  toCPU.ar.bits.id     := 0.U
  toCPU.ar.bits.len    := 0.U
  toCPU.ar.bits.lock   := 0.U
  toCPU.ar.bits.prot   := 0.U
  toCPU.ar.bits.qos    := 0.U
  toCPU.ar.bits.region := 0.U
  toCPU.ar.bits.size   := 0.U
  toCPU.ar.bits.user   := 0.U
  toCPU.ar.valid       := 0.B
}

private class ToDevice(toDevice: AXI_BUNDLE, fifo: QueueIO[UInt], devAddr: UInt)(implicit val p: Parameters) extends SimParams {
  val ARVALID = RegInit(0.B); toDevice.ar.valid := ARVALID
  val RREADY  = RegInit(0.B); toDevice.r .ready := RREADY && fifo.enq.ready

  val len = RegInit(0.U(32.W))

  when(toDevice.r.fire) {
    when(toDevice.r.bits.last) { RREADY := 0.B }
  }.elsewhen(toDevice.ar.fire) {
    ARVALID := 0.B
    RREADY  := 1.B
  }

  toDevice.ar.bits.addr   := devAddr
  toDevice.ar.bits.burst  := 1.U
  toDevice.ar.bits.cache  := 0.U
  toDevice.ar.bits.id     := 2.U
  toDevice.ar.bits.len    := len
  toDevice.ar.bits.lock   := 0.U
  toDevice.ar.bits.prot   := 0.U
  toDevice.ar.bits.qos    := 0.U
  toDevice.ar.bits.region := 0.U
  toDevice.ar.bits.size   := axSize.U
  toDevice.ar.bits.user   := 0.U

  toDevice.b.ready := 0.B

  toDevice.w.bits.data := 0.U
  toDevice.w.bits.last := 0.B
  toDevice.w.bits.strb := 0.U
  toDevice.w.bits.user := 0.U
  toDevice.w.valid     := 0.B

  toDevice.aw.bits.addr   := 0.U
  toDevice.aw.bits.burst  := 1.U
  toDevice.aw.bits.cache  := 0.U
  toDevice.aw.bits.id     := 0.U
  toDevice.aw.bits.len    := 0.U
  toDevice.aw.bits.lock   := 0.U
  toDevice.aw.bits.prot   := 0.U
  toDevice.aw.bits.qos    := 0.U
  toDevice.aw.bits.region := 0.U
  toDevice.aw.bits.size   := 0.U
  toDevice.aw.bits.user   := 0.U
  toDevice.aw.valid       := 0.B
}

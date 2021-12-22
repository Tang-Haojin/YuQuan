package sim.peripheral.dmac

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import sim._

class DMAC(implicit val p: Parameters) extends RawModule with SimParams {
  val io = IO(new Bundle {
    val toCPU    = new AXI_BUNDLE
    val fromCPU  = new AxiSlaveIO
  })

  dontTouch(io)

  private val idle::busy::Nil = Enum(2)

  withClockAndReset(io.fromCPU.basic.ACLK, !io.fromCPU.basic.ARESETn) {
    val regRAddr    = RegInit(0.U(xlen.W))
    val regWAddr    = RegInit(0.U(xlen.W))
    val regTransLen = RegInit(0.U(xlen.W))
    val regFree     = RegInit(1.B)

    val fifo = Module(new YQueue(UInt(xlen.W), 8))
    fifo.io.enq.valid := io.toCPU.r.fire
    fifo.io.enq.bits  := io.toCPU.r.bits.data
    fifo.io.deq.ready := io.toCPU.w.fire

    val toCPU    = new ToCPU(io.toCPU, fifo.io, regFree, regWAddr, regRAddr)
    val fromCPU  = new FromCPU(io.fromCPU.channel, toCPU, regRAddr, regWAddr, regTransLen, regFree)
  }
}

private class FromCPU(fromCPU: AXI_BUNDLE, toCPU: ToCPU, rAddr: UInt, wAddr: UInt, transLen: UInt, free: Bool)(implicit val p: Parameters) extends SimParams {
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
    is(DMAC.READ_ADDR_REG.U)   { wireRawRData := rAddr }
    is(DMAC.WRITE_ADDR_REG.U)  { wireRawRData := wAddr }
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
      is(DMAC.READ_ADDR_REG.U)   { rAddr  := wireWData }
      is(DMAC.WRITE_ADDR_REG.U)  { wAddr  := wireWData }
      is(DMAC.TRANS_LENTH_REG.U) { transLen := wireWData }
      is(DMAC.DMAC_STATUS_REG.U) {
        free := 0.B
        toCPU.originLen := transLen - 1.U
        toCPU.len       := transLen - 1.U
        toCPU.ARVALID   := 1.B
        toCPU.AWVALID   := 1.B
        toCPU.WVALID    := 1.B
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

private class ToCPU(toCPU: AXI_BUNDLE, fifo: QueueIO[UInt], free: Bool, wAddr: UInt, rAddr: UInt)(implicit val p: Parameters) extends SimParams {
  val AWVALID = RegInit(0.B); toCPU.aw.valid := AWVALID
  val WVALID  = RegInit(0.B); toCPU.w .valid := WVALID && fifo.deq.valid
  val BREADY  = RegInit(0.B); toCPU.b .ready := BREADY
  val ARVALID = RegInit(0.B); toCPU.ar.valid := ARVALID
  val RREADY  = RegInit(0.B); toCPU.r .ready := RREADY && fifo.enq.ready

  val originLen = RegInit(0.U(32.W))
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

  when(toCPU.r.fire) {
    when(toCPU.r.bits.last) { RREADY := 0.B }
  }.elsewhen(toCPU.ar.fire) {
    ARVALID := 0.B
    RREADY  := 1.B
  }

  toCPU.w.bits.last := wireWLAST
  toCPU.w.bits.data := fifo.deq.bits
  toCPU.w.bits.strb := Fill(xlen / 8, 1.B)
  toCPU.w.bits.user := 0.U

  toCPU.aw.bits.addr   := wAddr
  toCPU.aw.bits.burst  := 1.U
  toCPU.aw.bits.cache  := 0.U
  toCPU.aw.bits.id     := 2.U
  toCPU.aw.bits.len    := originLen
  toCPU.aw.bits.lock   := 0.U
  toCPU.aw.bits.prot   := 0.U
  toCPU.aw.bits.qos    := 0.U
  toCPU.aw.bits.region := 0.U
  toCPU.aw.bits.size   := axSize.U
  toCPU.aw.bits.user   := 0.U

  toCPU.ar.bits.addr   := rAddr
  toCPU.ar.bits.burst  := 1.U
  toCPU.ar.bits.cache  := 0.U
  toCPU.ar.bits.id     := 2.U
  toCPU.ar.bits.len    := originLen
  toCPU.ar.bits.lock   := 0.U
  toCPU.ar.bits.prot   := 0.U
  toCPU.ar.bits.qos    := 0.U
  toCPU.ar.bits.region := 0.U
  toCPU.ar.bits.size   := axSize.U
  toCPU.ar.bits.user   := 0.U
}

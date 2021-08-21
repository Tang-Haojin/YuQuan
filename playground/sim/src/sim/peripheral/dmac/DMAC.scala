package sim.peripheral.dmac

import chisel3._
import chisel3.util._

import tools._
import cpu.config.GeneralConfig._

class DMAC extends RawModule {
  val io = IO(new Bundle {
    val toCPU    = new AxiMasterChannel
    val toDevice = new AxiMasterChannel
    val fromCPU  = new AxiSlaveIO
  })

  val idle::busy::Nil = Enum(2)

  withClockAndReset(io.fromCPU.basic.ACLK, !io.fromCPU.basic.ARESETn) {
    val regDeviceAddr = RegInit(0.U(32.W))
    val regCpuAddr    = RegInit(0.U(32.W))
    val regTransLen   = RegInit(0.U(32.W))
    val regFree       = RegInit(1.B)

    val state = RegInit(UInt(1.W), idle)

    val fifo = Module(new Queue(UInt(XLEN.W), 8, flow = true))
    fifo.io.enq.valid := io.toDevice.axiRd.RREADY && io.toDevice.axiRd.RVALID
    fifo.io.enq.bits  := io.toDevice.axiRd.RDATA
    fifo.io.deq.ready := io.toCPU.axiWd.WREADY && io.toCPU.axiWd.WVALID

    object ToCPU {
      val AWVALID = RegInit(0.B); io.toCPU.axiWa.AWVALID := AWVALID
      val WVALID  = RegInit(0.B); io.toCPU.axiWd.WVALID  := WVALID && fifo.io.deq.valid
      val BREADY  = RegInit(0.B); io.toCPU.axiWr.BREADY  := BREADY

      val len = RegInit(0.U(32.W))

      when(io.toCPU.axiWa.AWREADY && io.toCPU.axiWa.AWVALID) {
        AWVALID := 0.B
        BREADY  := 1.B
      }

      when(io.toCPU.axiWd.WREADY && io.toCPU.axiWd.WVALID) {
        when(len === 0.U) {
          WVALID := 0.B
          BREADY := 1.B
          io.toCPU.axiWd.WLAST := 1.B
        }.otherwise { len := len - 1.U }
      }

      io.toCPU.axiWd.WDATA := fifo.io.deq.bits
      io.toCPU.axiWd.WLAST := 0.B
      io.toCPU.axiWd.WSTRB := Fill(XLEN / 8, 1.B)
      io.toCPU.axiWd.WUSER := 0.U

      io.toCPU.axiWa.AWADDR := regCpuAddr
      io.toCPU.axiWa.AWBURST := 1.U
      io.toCPU.axiWa.AWCACHE := 0.U
      io.toCPU.axiWa.AWID := 2.U
      io.toCPU.axiWa.AWLEN := ToDevice.len
      io.toCPU.axiWa.AWLOCK := 0.U
      io.toCPU.axiWa.AWPROT := 0.U
      io.toCPU.axiWa.AWQOS := 0.U
      io.toCPU.axiWa.AWREGION := 0.U
      io.toCPU.axiWa.AWSIZE := AxSIZE.U
      io.toCPU.axiWa.AWUSER := 0.U

      io.toCPU.axiRd.RREADY := 0.B

      io.toCPU.axiRa.ARADDR   := 0.U
      io.toCPU.axiRa.ARBURST  := 1.U
      io.toCPU.axiRa.ARCACHE  := 0.U
      io.toCPU.axiRa.ARID     := 0.U
      io.toCPU.axiRa.ARLEN    := 0.U
      io.toCPU.axiRa.ARLOCK   := 0.U
      io.toCPU.axiRa.ARPROT   := 0.U
      io.toCPU.axiRa.ARQOS    := 0.U
      io.toCPU.axiRa.ARREGION := 0.U
      io.toCPU.axiRa.ARSIZE   := 0.U
      io.toCPU.axiRa.ARUSER   := 0.U
      io.toCPU.axiRa.ARVALID  := 0.B
    }
    
    object ToDevice {
      val ARVALID = RegInit(0.B); io.toDevice.axiRa.ARVALID := ARVALID
      val RREADY  = RegInit(0.B); io.toDevice.axiRd.RREADY  := RREADY && fifo.io.enq.ready

      val len = RegInit(0.U(32.W))

      when(io.toDevice.axiRd.RREADY && io.toDevice.axiRd.RVALID) {
        when(io.toDevice.axiRd.RLAST) {
          RREADY  := 0.B
          ARVALID := 1.B
        }
      }.elsewhen(io.toDevice.axiRa.ARREADY && io.toDevice.axiRa.ARVALID) {
        ARVALID := 0.B
        RREADY  := 1.B
      }

      io.toDevice.axiRa.ARADDR   := regDeviceAddr
      io.toDevice.axiRa.ARBURST  := 1.U
      io.toDevice.axiRa.ARCACHE  := 0.U
      io.toDevice.axiRa.ARID     := 2.U
      io.toDevice.axiRa.ARLEN    := len
      io.toDevice.axiRa.ARLOCK   := 0.U
      io.toDevice.axiRa.ARPROT   := 0.U
      io.toDevice.axiRa.ARQOS    := 0.U
      io.toDevice.axiRa.ARREGION := 0.U
      io.toDevice.axiRa.ARSIZE   := AxSIZE.U
      io.toDevice.axiRa.ARUSER   := 0.U

      io.toDevice.axiWr.BREADY := 0.B

      io.toDevice.axiWd.WDATA  := 0.U
      io.toDevice.axiWd.WLAST  := 0.B
      io.toDevice.axiWd.WSTRB  := 0.U
      io.toDevice.axiWd.WUSER  := 0.U
      io.toDevice.axiWd.WVALID := 0.B

      io.toDevice.axiWa.AWADDR := 0.U
      io.toDevice.axiWa.AWBURST := 1.U
      io.toDevice.axiWa.AWCACHE := 0.U
      io.toDevice.axiWa.AWID := 0.U
      io.toDevice.axiWa.AWLEN := 0.U
      io.toDevice.axiWa.AWLOCK := 0.U
      io.toDevice.axiWa.AWPROT := 0.U
      io.toDevice.axiWa.AWQOS := 0.U
      io.toDevice.axiWa.AWREGION := 0.U
      io.toDevice.axiWa.AWSIZE := 0.U
      io.toDevice.axiWa.AWUSER := 0.U
      io.toDevice.axiWa.AWVALID := 0.B
    }

    object FromCPU {
      val AWREADY = RegInit(1.B); io.fromCPU.channel.axiWa.AWREADY := AWREADY
      val WREADY  = RegInit(0.B); io.fromCPU.channel.axiWd.WREADY  := WREADY
      val BVALID  = RegInit(0.B); io.fromCPU.channel.axiWr.BVALID  := BVALID
      val ARREADY = RegInit(1.B); io.fromCPU.channel.axiRa.ARREADY := ARREADY
      val RVALID  = RegInit(0.B); io.fromCPU.channel.axiRd.RVALID  := RVALID

      val RID    = RegInit(0.U(IDLEN.W)); io.fromCPU.channel.axiRd.RID := RID
      val BID    = RegInit(0.U(IDLEN.W)); io.fromCPU.channel.axiWr.BID := BID
      val AWADDR = RegInit(0.U(ALEN.W))
      val RDATA  = RegInit(0.U(XLEN.W)); io.fromCPU.channel.axiRd.RDATA := RDATA

      val wireRawRData = WireDefault(0.U(32.W))
      val wireReadOff  = io.fromCPU.channel.axiRa.ARADDR(AxSIZE - 1, 0)
      val wireWriteOff = AWADDR(AxSIZE - 1, 0)
      val wireRawWData = io.fromCPU.channel.axiWd.WDATA
      val wireWData = VecInit((0 until XLEN / 8).map { i => wireRawWData >> (8 * i) })(wireWriteOff)
      switch(io.fromCPU.channel.axiRa.ARADDR) {
        is(DMAC.DEVICE_ADDR_REG.U) { wireRawRData := regDeviceAddr }
        is(DMAC.MEMORY_ADDR_REG.U) { wireRawRData := regCpuAddr }
        is(DMAC.TRANS_LENTH_REG.U) { wireRawRData := regTransLen }
        is(DMAC.DMAC_STATUS_REG.U) { wireRawRData := 0.U(31.W) ## regFree }
      }

      when(io.fromCPU.channel.axiRd.RVALID && io.fromCPU.channel.axiRd.RREADY) {
        RVALID  := 0.B
        ARREADY := 1.B
      }.elsewhen(io.fromCPU.channel.axiRa.ARVALID && io.fromCPU.channel.axiRa.ARREADY) {
        RDATA   := VecInit((0 until XLEN / 8).map { i => wireRawRData << (8 * i) })(wireReadOff)
        RID     := io.fromCPU.channel.axiRa.ARID
        ARREADY := 0.B
        RVALID  := 1.B
      }

      when(io.fromCPU.channel.axiWa.AWVALID && io.fromCPU.channel.axiWa.AWREADY) {
        AWADDR  := io.fromCPU.channel.axiWa.AWADDR
        BID     := io.fromCPU.channel.axiWa.AWID
        AWREADY := 0.B
        WREADY  := 1.B
      }

      when(io.fromCPU.channel.axiWd.WVALID && io.fromCPU.channel.axiWd.WREADY) {
        switch(AWADDR) {
          is(DMAC.DEVICE_ADDR_REG.U) { regDeviceAddr := wireWData }
          is(DMAC.MEMORY_ADDR_REG.U) { regCpuAddr    := wireWData }
          is(DMAC.TRANS_LENTH_REG.U) { regTransLen   := wireWData }
          is(DMAC.DMAC_STATUS_REG.U) {
            regFree := 0.B
            state   := busy
            ToDevice.len := regTransLen(31, 3) - 1.U
            ToCPU.len    := regTransLen(31, 3) - 1.U
            ToDevice.ARVALID := 1.B
            ToCPU.AWVALID    := 1.B
            ToCPU.WVALID     := 1.B
          }
      }
        WREADY := 0.B
        BVALID := 1.B
      }

      when(io.fromCPU.channel.axiWr.BVALID && io.fromCPU.channel.axiWr.BREADY) {
        AWREADY := 1.B
        BVALID  := 0.B
      }
    }
  }
}

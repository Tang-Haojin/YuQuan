package tools.axi2apb

import chisel3._
import chisel3.util._

import java.io.File
import cpu.config.GeneralConfig._
import tools._

class Axi2ApbIO extends Bundle {
  val axi_s = new AxiSlaveIO
  val apb_m = Flipped(new ApbSlaveIO)
}

class InnerAxi2ApbIO extends Bundle {
  val ACLK    = Input (Bool())
  val ARESETn = Input (Bool())

  val AWID    = Input (UInt(IDLEN.W))
  val AWADDR  = Input (UInt(ALEN.W))
  val AWLEN   = Input (UInt(4.W))
  val AWSIZE  = Input (UInt(2.W))
  val AWVALID = Input (Bool())
  val AWREADY = Output(Bool())

  val WDATA   = Input (UInt(32.W))
  val WSTRB   = Input (UInt((32 / 8).W))
  val WLAST   = Input (Bool())
  val WVALID  = Input (Bool())
  val WREADY  = Output(Bool())

  val BID     = Output(UInt(IDLEN.W))
  val BRESP   = Output(UInt(2.W))
  val BVALID  = Output(Bool())
  val BREADY  = Input (Bool())

  val ARID    = Input (UInt(IDLEN.W))
  val ARADDR  = Input (UInt(ALEN.W))
  val ARLEN   = Input (UInt(4.W))
  val ARSIZE  = Input (UInt(2.W))
  val ARVALID = Input (Bool())
  val ARREADY = Output(Bool())

  val RID     = Output(UInt(IDLEN.W))
  val RDATA   = Output(UInt(32.W))
  val RRESP   = Output(UInt(2.W))
  val RLAST   = Output(Bool())
  val RVALID  = Output(Bool())
  val RREADY  = Input(Bool())

  val pclk    = Output(Bool())
  val presetn = Output(Bool())
  val psel    = Output(Bool())
  val penable = Output(Bool())
  val pwrite  = Output(Bool())
  val paddr   = Output(UInt(32.W))
  val pwdata  = Output(UInt(32.W))
  val pwstrb  = Output(UInt((32 / 8).W))
  val prdata  = Input(UInt(32.W))
  val pslverr = Input(Bool())
  val pready  = Input(Bool())
}

class inner_axi2apb extends BlackBox with HasBlackBoxPath {
  val io = IO(new InnerAxi2ApbIO)
  addPath(new File("playground/src/tools/axi2apb/inner/inner_axi2apb.v").getCanonicalPath)
}

class Axi2Apb extends RawModule {
  val io = IO(new Axi2ApbIO)

  withClockAndReset(io.axi_s.basic.ACLK, !io.axi_s.basic.ARESETn) {
    val idle::reading::writing::Nil = Enum(3)
    val state = RegInit(UInt(2.W), idle)
    val wFirst = RegInit(0.B) // to avoid starvation
    wFirst := ~wFirst

    val inner_ARADDR = RegInit(0.U(ALEN.W))
    val inner_ARID   = RegInit(0.U(IDLEN.W))
    val inner_AWADDR = RegInit(0.U(ALEN.W))
    val inner_AWID   = RegInit(0.U(IDLEN.W))
    val inner_RDATA  = RegInit(VecInit(Seq.fill(XLEN / 32)(0.U(32.W))))
    val inner_WDATA  = RegInit(0.U(XLEN.W))
    val inner_WSTRB  = RegInit(0.U((XLEN / 8).W))

    val inner_ARVALID = RegInit(0.B)
    val inner_RREADY  = 1.B
    val inner_AWVALID = RegInit(0.B)
    val inner_WVALID  = RegInit(0.B)
    val inner_BREADY  = 1.B

    val regARREADY = RegInit(1.B)
    val regRVALID  = RegInit(0.B)
    val regAWREADY = RegInit(1.B)
    val regWREADY  = RegInit(1.B)
    val regBVALID  = RegInit(0.B)

    val wireARREADY = WireDefault(Bool(), regARREADY); io.axi_s.channel.axiRa.ARREADY := wireARREADY
    val wireRVALID  = WireDefault(Bool(), regRVALID ); io.axi_s.channel.axiRd.RVALID  := wireRVALID
    val wireAWREADY = WireDefault(Bool(), regAWREADY); io.axi_s.channel.axiWa.AWREADY := wireAWREADY
    val wireWREADY  = WireDefault(Bool(), regWREADY ); io.axi_s.channel.axiWd.WREADY  := wireWREADY
    val wireBVALID  = WireDefault(Bool(), regBVALID ); io.axi_s.channel.axiWr.BVALID  := wireBVALID

    val read_pending  = RegInit(0.U(Seq(1, log2Ceil(XLEN / 8) - 2).max.W))
    val write_pending = RegInit(0.U(Seq(1, log2Ceil(XLEN / 8) - 2).max.W))
    val read_counter  = RegInit(0.U(Seq(1, log2Ceil(XLEN / 8) - 2).max.W))

    val inner_Axi2Apb = Module(new inner_axi2apb)
    inner_Axi2Apb.io.ACLK := io.axi_s.basic.ACLK.asBool()
    inner_Axi2Apb.io.ARESETn := io.axi_s.basic.ARESETn

    inner_Axi2Apb.io.ARID    := inner_ARID
    inner_Axi2Apb.io.ARADDR  := inner_ARADDR
    inner_Axi2Apb.io.ARLEN   := 0.U
    inner_Axi2Apb.io.ARSIZE  := 2.U
    inner_Axi2Apb.io.ARVALID := inner_ARVALID

    inner_Axi2Apb.io.RREADY  := inner_RREADY

    inner_Axi2Apb.io.AWID    := inner_AWID
    inner_Axi2Apb.io.AWADDR  := inner_AWADDR
    inner_Axi2Apb.io.AWLEN   := 0.U
    inner_Axi2Apb.io.AWSIZE  := 2.U
    inner_Axi2Apb.io.AWVALID := inner_AWVALID

    inner_Axi2Apb.io.WDATA  := inner_WDATA(31, 0)
    inner_Axi2Apb.io.WSTRB  := inner_WSTRB(32 / 8 - 1, 0)
    inner_Axi2Apb.io.WLAST  := 1.B
    inner_Axi2Apb.io.WVALID := inner_WVALID

    inner_Axi2Apb.io.BREADY := inner_BREADY

    io.apb_m.pclk    := inner_Axi2Apb.io.pclk.asClock()
    io.apb_m.presetn := inner_Axi2Apb.io.presetn
    io.apb_m.psel    := inner_Axi2Apb.io.psel
    io.apb_m.penable := inner_Axi2Apb.io.penable
    io.apb_m.paddr   := inner_Axi2Apb.io.paddr
    io.apb_m.pwrite  := inner_Axi2Apb.io.pwrite
    io.apb_m.pwdata  := inner_Axi2Apb.io.pwdata
    io.apb_m.pwstrb  := inner_Axi2Apb.io.pwstrb

    inner_Axi2Apb.io.pready  := io.apb_m.pready
    inner_Axi2Apb.io.pslverr := io.apb_m.pslverr
    inner_Axi2Apb.io.prdata  := io.apb_m.prdata

    io.axi_s.channel.axiRd.RID   := inner_ARID
    io.axi_s.channel.axiRd.RLAST := 1.B
    io.axi_s.channel.axiRd.RRESP := 0.U
    io.axi_s.channel.axiRd.RUSER := DontCare
    io.axi_s.channel.axiRd.RDATA := inner_RDATA.asUInt()

    io.axi_s.channel.axiWr.BID   := inner_AWID
    io.axi_s.channel.axiWr.BRESP := 0.U
    io.axi_s.channel.axiWr.BUSER := DontCare

    val readAddrReq  = regARREADY && io.axi_s.channel.axiRa.ARVALID
    val writeAddrReq = regAWREADY && io.axi_s.channel.axiWa.AWVALID
    val writeDataReq = regWREADY  && io.axi_s.channel.axiWd.WVALID

    val readAddrAcpt  = io.axi_s.channel.axiRa.ARREADY && io.axi_s.channel.axiRa.ARVALID
    val readDataAcpt  = io.axi_s.channel.axiRd.RREADY  && io.axi_s.channel.axiRd.RVALID
    val writeAddrAcpt = io.axi_s.channel.axiWa.AWREADY && io.axi_s.channel.axiWa.AWVALID
    val writeDataAcpt = io.axi_s.channel.axiWd.WREADY  && io.axi_s.channel.axiWd.WVALID
    val writeRespAcpt = io.axi_s.channel.axiWr.BREADY  && io.axi_s.channel.axiWr.BVALID

    val inner_readAddrAcpt  = inner_Axi2Apb.io.ARREADY && inner_Axi2Apb.io.ARVALID
    val inner_readDataAcpt  = inner_Axi2Apb.io.RREADY  && inner_Axi2Apb.io.RVALID
    val inner_WriteAddrAcpt = inner_Axi2Apb.io.AWREADY && inner_Axi2Apb.io.AWVALID
    val inner_WriteDataAcpt = inner_Axi2Apb.io.WREADY  && inner_Axi2Apb.io.WVALID
    val inner_WriteRespAcpt = inner_Axi2Apb.io.BREADY  && inner_Axi2Apb.io.BVALID

    when(readAddrAcpt) {
      state := reading
      regARREADY := 0.B
      inner_ARVALID := 1.B
      inner_ARID := io.axi_s.channel.axiRa.ARID
      inner_ARADDR := io.axi_s.channel.axiRa.ARADDR(ALEN - 1, log2Ceil(XLEN / 8)) ## 0.U(log2Ceil(XLEN / 8).W)
      read_counter := 0.U
      when(io.axi_s.channel.axiRa.ARSIZE < 3.U) { read_pending := 0.U }
      for (i <- 3 to log2Ceil(XLEN / 8))
        when(io.axi_s.channel.axiRa.ARSIZE === i.U) { read_pending := Fill(i - 2, 1.B) }
    }

    when(writeAddrAcpt) {
      state := writing
      regAWREADY := 0.B
      inner_AWVALID := 1.B
      inner_AWID := io.axi_s.channel.axiWa.AWID
      inner_AWADDR := io.axi_s.channel.axiWa.AWADDR
      when(io.axi_s.channel.axiWa.AWSIZE < 3.U) { write_pending := 0.U }
      for (i <- 3 to log2Ceil(XLEN / 8))
        when(io.axi_s.channel.axiWa.AWSIZE === i.U) { write_pending := Fill(i - 2, 1.B) }
    }

    when(writeDataAcpt) {
      state := writing
      regWREADY := 0.B
      inner_WVALID := 1.B
      inner_WDATA := io.axi_s.channel.axiWd.WDATA
    }

    when(state === idle) {
      when(readAddrReq && (writeAddrReq || writeDataReq)) {
        when(wFirst) { wireARREADY := 0.B }
        .otherwise   { wireAWREADY := 0.B; wireWREADY := 0.B }
      }
    }

    when(state === reading) {
      when(inner_readAddrAcpt) {
        inner_ARVALID := 0.B
      }

      when(inner_readDataAcpt) {
        inner_RDATA(read_counter) := inner_Axi2Apb.io.RDATA
        when(read_pending === 0.U) { regRVALID := 1.B }
        .otherwise {
          inner_ARVALID := 1.B
          inner_ARADDR := inner_ARADDR + 4.U
          read_pending := read_pending - 1.U
          read_counter := read_counter + 1.U
        }
      }

      when(readDataAcpt) {
        regRVALID  := 0.B
        regARREADY := 1.B
        state      := idle
      }
    }

    when(state === writing) {
      when(inner_WriteAddrAcpt) {
        inner_AWVALID := 0.B
      }

      when(inner_WriteDataAcpt) {
        inner_WVALID := 0.B
      }

      when(inner_WriteRespAcpt) {
        inner_WDATA := inner_WDATA >> 32.U
        inner_WSTRB := inner_WSTRB >> (32 / 8).U
        when(write_pending === 0.U) { regBVALID := 1.B }
        .otherwise {
          inner_AWVALID := 1.B
          inner_WVALID  := 1.B
          inner_AWADDR  := inner_AWADDR + 4.U
          read_pending  := read_pending - 1.U
        }
      }

      when(writeRespAcpt) {
        regBVALID  := 0.B
        regAWREADY := 1.B
        regWREADY  := 1.B
        state      := idle
      }
    }
  }
}

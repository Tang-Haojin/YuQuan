package utils.axi2apb

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import java.io.File
import utils._

class Axi2ApbIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val axi_s = new AxiSlaveIO
  val apb_m = Flipped(new ApbSlaveIO)
}

class InnerAxi2ApbIO(implicit val p: Parameters) extends Bundle with UtilsParams {
  val ACLK    = Input (Bool())
  val ARESETn = Input (Bool())

  val AWID    = Input (UInt(idlen.W))
  val AWADDR  = Input (UInt(alen.W))
  val AWLEN   = Input (UInt(4.W))
  val AWSIZE  = Input (UInt(2.W))
  val AWVALID = Input (Bool())
  val AWREADY = Output(Bool())

  val WDATA   = Input (UInt(32.W))
  val WSTRB   = Input (UInt((32 / 8).W))
  val WLAST   = Input (Bool())
  val WVALID  = Input (Bool())
  val WREADY  = Output(Bool())

  val BID     = Output(UInt(idlen.W))
  val BRESP   = Output(UInt(2.W))
  val BVALID  = Output(Bool())
  val BREADY  = Input (Bool())

  val ARID    = Input (UInt(idlen.W))
  val ARADDR  = Input (UInt(alen.W))
  val ARLEN   = Input (UInt(4.W))
  val ARSIZE  = Input (UInt(2.W))
  val ARVALID = Input (Bool())
  val ARREADY = Output(Bool())

  val RID     = Output(UInt(idlen.W))
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

class inner_axi2apb(implicit val p: Parameters) extends BlackBox with HasBlackBoxPath with UtilsParams {
  val io = IO(new InnerAxi2ApbIO)
  addPath(new File("utils/src/axi2apb/inner/inner_axi2apb.v").getCanonicalPath)
}

class Axi2Apb(implicit val p: Parameters) extends RawModule with UtilsParams {
  val io = IO(new Axi2ApbIO)

  withClockAndReset(io.axi_s.basic.ACLK, !io.axi_s.basic.ARESETn) {
    val idle::reading::writing::Nil = Enum(3)
    val state = RegInit(UInt(2.W), idle)
    val wFirst = RegInit(0.B) // to avoid starvation
    wFirst := ~wFirst

    val inner_ARADDR = RegInit(0.U(alen.W))
    val inner_ARID   = RegInit(0.U(idlen.W))
    val inner_AWADDR = RegInit(0.U(alen.W))
    val inner_AWID   = RegInit(0.U(idlen.W))
    val inner_RDATA  = RegInit(VecInit(Seq.fill(xlen / 32)(0.U(32.W))))
    val inner_WDATA  = RegInit(0.U(xlen.W))
    val inner_WSTRB  = RegInit(0.U((xlen / 8).W))

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

    val wireARREADY = WireDefault(Bool(), regARREADY); io.axi_s.channel.ar.ready := wireARREADY
    val wireRVALID  = WireDefault(Bool(), regRVALID ); io.axi_s.channel.r .valid  := wireRVALID
    val wireAWREADY = WireDefault(Bool(), regAWREADY); io.axi_s.channel.aw.ready := wireAWREADY
    val wireWREADY  = WireDefault(Bool(), regWREADY ); io.axi_s.channel.w .ready  := wireWREADY
    val wireBVALID  = WireDefault(Bool(), regBVALID ); io.axi_s.channel.b .valid  := wireBVALID

    val read_pending  = RegInit(0.U(Seq(1, log2Ceil(xlen / 8) - 2).max.W))
    val write_pending = RegInit(0.U(Seq(1, log2Ceil(xlen / 8) - 2).max.W))
    val read_counter  = RegInit(0.U(Seq(1, log2Ceil(xlen / 8) - 2).max.W))

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

    io.axi_s.channel.r.bits.id   := inner_ARID
    io.axi_s.channel.r.bits.last := 1.B
    io.axi_s.channel.r.bits.resp := 0.U
    io.axi_s.channel.r.bits.user := DontCare
    io.axi_s.channel.r.bits.data := inner_RDATA.asUInt()

    io.axi_s.channel.b.bits.id   := inner_AWID
    io.axi_s.channel.b.bits.resp := 0.U
    io.axi_s.channel.b.bits.user := DontCare

    val readAddrReq  = regARREADY && io.axi_s.channel.ar.valid
    val writeAddrReq = regAWREADY && io.axi_s.channel.aw.valid
    val writeDataReq = regWREADY  && io.axi_s.channel.w .valid

    val readAddrAcpt  = io.axi_s.channel.ar.fire()
    val readDataAcpt  = io.axi_s.channel.r .fire()
    val writeAddrAcpt = io.axi_s.channel.aw.fire()
    val writeDataAcpt = io.axi_s.channel.w .fire()
    val writeRespAcpt = io.axi_s.channel.b .fire()

    val inner_readAddrAcpt  = inner_Axi2Apb.io.ARREADY && inner_Axi2Apb.io.ARVALID
    val inner_readDataAcpt  = inner_Axi2Apb.io.RREADY  && inner_Axi2Apb.io.RVALID
    val inner_WriteAddrAcpt = inner_Axi2Apb.io.AWREADY && inner_Axi2Apb.io.AWVALID
    val inner_WriteDataAcpt = inner_Axi2Apb.io.WREADY  && inner_Axi2Apb.io.WVALID
    val inner_WriteRespAcpt = inner_Axi2Apb.io.BREADY  && inner_Axi2Apb.io.BVALID

    when(readAddrAcpt) {
      state := reading
      regARREADY := 0.B
      inner_ARVALID := 1.B
      inner_ARID := io.axi_s.channel.ar.bits.id
      inner_ARADDR := io.axi_s.channel.ar.bits.addr(alen - 1, log2Ceil(xlen / 8)) ## 0.U(log2Ceil(xlen / 8).W)
      read_counter := 0.U
      when(io.axi_s.channel.ar.bits.size < 3.U) { read_pending := 0.U }
      for (i <- 3 to log2Ceil(xlen / 8))
        when(io.axi_s.channel.ar.bits.size === i.U) { read_pending := Fill(i - 2, 1.B) }
    }

    when(writeAddrAcpt) {
      state := writing
      regAWREADY := 0.B
      inner_AWVALID := 1.B
      inner_AWID := io.axi_s.channel.aw.bits.id
      inner_AWADDR := io.axi_s.channel.aw.bits.addr
      when(io.axi_s.channel.aw.bits.size < 3.U) { write_pending := 0.U }
      for (i <- 3 to log2Ceil(xlen / 8))
        when(io.axi_s.channel.aw.bits.size === i.U) { write_pending := Fill(i - 2, 1.B) }
    }

    when(writeDataAcpt) {
      state := writing
      regWREADY := 0.B
      inner_WVALID := 1.B
      inner_WDATA := io.axi_s.channel.w.bits.data
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

package peripheral

import chisel3._

import tools._

import cpu.config.GeneralConfig._

class PlicIO extends AxiSlaveIO {
  val inter = Input (Vec(1024, Bool()))
  val eip   = Output(Bool())
}

class Plic extends RawModule {
  val io = IO(new PlicIO)

  io.channel.axiWr.BRESP := 0.U
  io.channel.axiWr.BUSER := DontCare

  io.channel.axiRd.RLAST := 1.B
  io.channel.axiRd.RUSER := DontCare
  io.channel.axiRd.RRESP := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(1.B); io.channel.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.channel.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.channel.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.channel.axiRd.RVALID  := RVALID

    val RID   = RegInit(0.U(IDLEN.W)); io.channel.axiRd.RID := RID
    val BID   = RegInit(0.U(IDLEN.W)); io.channel.axiWr.BID := BID
    val RDATA = RegInit(0.U(XLEN.W))
    val WADDR = RegInit(0.U(ALEN.W))
    val WDATA = RegInit(0.U(XLEN.W))
    val WSTRB = RegInit(0.U((XLEN / 8).W))

    val offset = RegInit(0.U(3.W))

    val isp = RegInit(VecInit(Seq.concat(Seq(0.U(32.W)), Seq.fill(15)(1.U(32.W)))))
    val ieb = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))
    val ipt = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))

    io.channel.axiRd.RDATA := VecInit((0 until 8).map { i => RDATA << (8 * i) })(offset)
    
    when(io.channel.axiRd.RVALID && io.channel.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.axiRa.ARVALID && io.channel.axiRa.ARREADY) {
      RID   := io.channel.axiRa.ARID
      RDATA := 0.U
      offset := io.channel.axiRa.ARADDR
      ARREADY := 0.B
      RVALID  := 1.B
      for (i <- 0 until 16) when(io.channel.axiRa.ARADDR === PLIC.Isp(i).U) { RDATA := isp(i) }
      for (i <- 0 until 0x80) when(io.channel.axiRa.ARADDR === PLIC.Ipb(i).U) { RDATA := io.inter(i) }
      when(io.channel.axiRa.ARADDR === PLIC.Ieb(10, 0).U) { RDATA := ieb(0) }
      when(io.channel.axiRa.ARADDR === PLIC.Ipt(0).U) { RDATA := ipt(0) }
      when(io.channel.axiRa.ARADDR === PLIC.Ic(0).U) { when(io.inter(10)) { RDATA := 10.U } }
    }
    
    when(io.channel.axiWa.AWVALID && io.channel.axiWa.AWREADY) {
      WADDR   := io.channel.axiWa.AWADDR
      BID     := io.channel.axiWa.AWID
      AWREADY := 0.B
    }

    when(io.channel.axiWd.WVALID && io.channel.axiWd.WREADY) {
      WDATA  := VecInit((0 until 8).map { i => io.channel.axiWd.WDATA >> (8 * i) })(WADDR)
      WSTRB  := VecInit((0 until 8).map { i => io.channel.axiWd.WSTRB >> i })(WADDR)
      WREADY := 0.B
    }

    when(!io.channel.axiWa.AWREADY && !io.channel.axiWd.WREADY && !io.channel.axiWr.BVALID) {
      AWREADY := 1.B
      WREADY  := 1.B
      BVALID  := 1.B
      for (i <- 1 until 16) when(WADDR === PLIC.Isp(i).U) { isp(i) := WDATA }
      when(WADDR === PLIC.Ieb(10, 0).U) { ieb(0) := WDATA }
      when(WADDR === PLIC.Ipt(0).U) { ipt(0) := WDATA }
    }

    when(io.channel.axiWr.BVALID && io.channel.axiWr.BREADY) {
      BVALID := 0.B
    }

    io.eip := ((isp(10) =/= 0.U) && io.inter(10))
  }
}

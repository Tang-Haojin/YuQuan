package peripheral

import chisel3._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.diplomacy.ValName

import utils._

class PlicIO(implicit p: Parameters, valName: ValName) extends AxiSlaveIO {
  val inter = Input (Vec(1024, Bool()))
  val eip   = Output(Bool())
}

class Plic(implicit val p: Parameters) extends RawModule with PeripheralParams {
  val io = IO(new PlicIO)

  io.channel.b.bits.resp := 0.U
  io.channel.b.bits.user := DontCare

  io.channel.r.bits.last := 1.B
  io.channel.r.bits.user := DontCare
  io.channel.r.bits.resp := 0.U

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.channel.aw.ready := AWREADY
    val WREADY  = RegInit(1.B); io.channel.w .ready := WREADY
    val BVALID  = RegInit(0.B); io.channel.b .valid := BVALID
    val ARREADY = RegInit(1.B); io.channel.ar.ready := ARREADY
    val RVALID  = RegInit(0.B); io.channel.r .valid := RVALID

    val RID   = RegInit(0.U(idlen.W)); io.channel.r.bits.id := RID
    val BID   = RegInit(0.U(idlen.W)); io.channel.b.bits.id := BID
    val RDATA = RegInit(0.U(xlen.W))
    val WADDR = RegInit(0.U(alen.W))
    val WDATA = RegInit(0.U(xlen.W))
    val WSTRB = RegInit(0.U((xlen / 8).W))

    val offset = RegInit(0.U(3.W))

    val isp = RegInit(VecInit(Seq.concat(Seq(0.U(32.W)), Seq.fill(15)(1.U(32.W)))))
    val ieb = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))
    val ipt = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))

    val sieb = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))
    val sipt = RegInit(VecInit(Seq.fill( 1)(0.U(32.W))))

    io.channel.r.bits.data := VecInit((0 until 8).map { i => RDATA << (8 * i) })(offset)
    
    when(io.channel.r.fire()) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(io.channel.ar.fire()) {
      RID   := io.channel.ar.bits.id
      RDATA := 0.U
      offset := io.channel.ar.bits.addr
      ARREADY := 0.B
      RVALID  := 1.B
      for (i <- 0 until 16) when(io.channel.ar.bits.addr === PLIC.M_Priority(i).U) { RDATA := isp(i) }
      for (i <- 0 until 0x80) when(io.channel.ar.bits.addr === PLIC.M_Pending(i).U) { RDATA := io.inter(i) }
      when(io.channel.ar.bits.addr === PLIC.M_Enable(10, 0).U) { RDATA := ieb(0) }
      when(io.channel.ar.bits.addr === PLIC.M_Threshold(0).U) { RDATA := ipt(0) }
      when(io.channel.ar.bits.addr === PLIC.M_CLAIM(0).U) { when(io.inter(10)) { RDATA := 10.U } }
      when(io.channel.ar.bits.addr === PLIC.S_Enable(10, 0).U) { RDATA := sieb(0) }
      when(io.channel.ar.bits.addr === PLIC.S_threshold(0).U) { RDATA := sipt(0) }
      when(io.channel.ar.bits.addr === PLIC.S_CLAIM(0).U) { when(io.inter(10)) { RDATA := 10.U } }
    }

    when(io.channel.aw.fire()) {
      WADDR   := io.channel.aw.bits.addr
      BID     := io.channel.aw.bits.id
      AWREADY := 0.B
    }

    when(io.channel.w.fire()) {
      WDATA  := VecInit((0 until 8).map { i => io.channel.w.bits.data >> (8 * i) })(WADDR)
      WSTRB  := VecInit((0 until 8).map { i => io.channel.w.bits.strb >> i })(WADDR)
      WREADY := 0.B
    }

    when(!io.channel.aw.ready && !io.channel.w.ready && !io.channel.b.valid) {
      AWREADY := 1.B
      WREADY  := 1.B
      BVALID  := 1.B
      for (i <- 1 until 16) when(WADDR === PLIC.M_Priority(i).U) { isp(i) := WDATA }
      when(WADDR === PLIC.M_Enable(10, 0).U) { ieb(0) := WDATA }
      when(WADDR === PLIC.M_Threshold(0).U) { ipt(0) := WDATA }
      when(WADDR === PLIC.S_Enable(10, 0).U) { sieb(0) := WDATA }
      when(WADDR === PLIC.S_threshold(0).U) { sipt(0) := WDATA }
    }

    when(io.channel.b.fire()) {
      BVALID := 0.B
    }

    io.eip := ((isp(10) =/= 0.U) && io.inter(10))
  }
}

package cpu

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._
import cpu.config._
import tools._

class AXIRMux extends Module {
  val io = IO(new Bundle {
    val axiRaIn0 = Flipped(new AXIra)
    val axiRaIn1 = Flipped(new AXIra)
    val axiRaOut = new AXIra
    val axiRdIn0 = Flipped(new AXIrd)
    val axiRdIn1 = Flipped(new AXIrd)
    val axiRdOut = new AXIrd
  })

  val pending = RegInit(0.U(2.W))

  val RID_FIFO   = Module(new Queue(UInt(IDLEN.W), 2))
  val RDATA_FIFO = Module(new Queue(UInt(XLEN.W), 2))
  val RRESP_FIFO = Module(new Queue(UInt(2.W), 2))
  val RLAST_FIFO = Module(new Queue(Bool(), 2))
  val RUSER_FIFO = Module(new Queue(UInt(1.W), 2))

  RID_FIFO.io.enq.valid   := 0.B; RID_FIFO.io.deq.ready   := 0.B
  RDATA_FIFO.io.enq.valid := 0.B; RDATA_FIFO.io.deq.ready := 0.B
  RRESP_FIFO.io.enq.valid := 0.B; RRESP_FIFO.io.deq.ready := 0.B
  RLAST_FIFO.io.enq.valid := 0.B; RLAST_FIFO.io.deq.ready := 0.B
  RUSER_FIFO.io.enq.valid := 0.B; RUSER_FIFO.io.deq.ready := 0.B

  RID_FIFO.io.enq.bits   := 0.U
  RDATA_FIFO.io.enq.bits := 0.U
  RRESP_FIFO.io.enq.bits := 0.U
  RLAST_FIFO.io.enq.bits := 0.B
  RUSER_FIFO.io.enq.bits := 0.U

  io.axiRaIn0.ARREADY := 0.B
  io.axiRaIn1.ARREADY := 0.B

  when(io.axiRaIn1.ARVALID) {
    io.axiRaIn1 <> io.axiRaOut
  }.otherwise {
    io.axiRaIn0 <> io.axiRaOut
  }

  io.axiRdIn0.RID    := 0xf.U
  io.axiRdIn1.RID    := 0xf.U
  io.axiRdIn0.RDATA  := 0.U
  io.axiRdIn1.RDATA  := 0.U
  io.axiRdIn0.RRESP  := 0.U
  io.axiRdIn1.RRESP  := 0.U
  io.axiRdIn0.RLAST  := 0.B
  io.axiRdIn1.RLAST  := 0.B
  io.axiRdIn0.RUSER  := 0.U
  io.axiRdIn1.RUSER  := 0.U
  io.axiRdIn0.RVALID := 0.B
  io.axiRdIn1.RVALID := 0.B

  when(io.axiRdOut.RVALID && io.axiRdIn1.RREADY && io.axiRdOut.RID === 1.U) {
    io.axiRdIn1 <> io.axiRdOut
  }.otherwise {
    io.axiRdIn0 <> io.axiRdOut

    when(!io.axiRdIn0.RREADY || (RID_FIFO.io.count =/= 0.U)) {
      io.axiRdOut.RREADY := RID_FIFO.io.enq.ready

      RID_FIFO.io.enq.bits   := io.axiRdOut.RID
      RDATA_FIFO.io.enq.bits := io.axiRdOut.RDATA
      RRESP_FIFO.io.enq.bits := io.axiRdOut.RRESP
      RLAST_FIFO.io.enq.bits := io.axiRdOut.RLAST
      RUSER_FIFO.io.enq.bits := io.axiRdOut.RUSER

      RID_FIFO.io.enq.valid   := io.axiRdOut.RVALID
      RDATA_FIFO.io.enq.valid := io.axiRdOut.RVALID
      RRESP_FIFO.io.enq.valid := io.axiRdOut.RVALID
      RLAST_FIFO.io.enq.valid := io.axiRdOut.RVALID
      RUSER_FIFO.io.enq.valid := io.axiRdOut.RVALID
    }

    when(RID_FIFO.io.count =/= 0.U) {
      io.axiRdIn0.RID    := RID_FIFO.io.deq.bits
      io.axiRdIn0.RDATA  := RDATA_FIFO.io.deq.bits
      io.axiRdIn0.RRESP  := RRESP_FIFO.io.deq.bits
      io.axiRdIn0.RLAST  := RLAST_FIFO.io.deq.bits
      io.axiRdIn0.RUSER  := RUSER_FIFO.io.deq.bits
      io.axiRdIn0.RVALID := RID_FIFO.io.deq.valid

      RID_FIFO.io.deq.ready   := io.axiRdIn0.RREADY
      RDATA_FIFO.io.deq.ready := io.axiRdIn0.RREADY
      RRESP_FIFO.io.deq.ready := io.axiRdIn0.RREADY
      RLAST_FIFO.io.deq.ready := io.axiRdIn0.RREADY
      RUSER_FIFO.io.deq.ready := io.axiRdIn0.RREADY
    }
  }

  when(pending === 2.U) {
    io.axiRaIn0.ARREADY := 0.B
    when(io.axiRdIn0.RREADY && io.axiRdIn0.RVALID) {
      pending := pending - 1.U
    }
  }.otherwise {
    when(io.axiRdIn0.RREADY && io.axiRdIn0.RVALID && !(io.axiRaIn0.ARREADY && io.axiRaIn0.ARVALID)) {
      pending := pending - 1.U
    }.elsewhen(!(io.axiRdIn0.RREADY && io.axiRdIn0.RVALID) && io.axiRaIn0.ARREADY && io.axiRaIn0.ARVALID) {
      pending := pending + 1.U + io.axiRaIn0.ARLEN
    }
  }
}

package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._

class AXIRMux(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val axiRaIn0 = Flipped(Irrevocable(new AXI_BUNDLE_AR))
    val axiRaIn1 = Flipped(Irrevocable(new AXI_BUNDLE_AR))
    val axiRaOut =         Irrevocable(new AXI_BUNDLE_AR)
    val axiRdIn0 =         Irrevocable(new AXI_BUNDLE_R)
    val axiRdIn1 =         Irrevocable(new AXI_BUNDLE_R)
    val axiRdOut = Flipped(Irrevocable(new AXI_BUNDLE_R))
  })

  val pending = RegInit(0.U(2.W))

  val RID_FIFO   = Module(new YQueue(UInt(idlen.W), 2))
  val RDATA_FIFO = Module(new YQueue(UInt(xlen.W), 2))
  val RRESP_FIFO = Module(new YQueue(UInt(2.W), 2))
  val RLAST_FIFO = Module(new YQueue(Bool(), 2))
  val RUSER_FIFO = Module(new YQueue(UInt(1.W), 2))

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

  io.axiRaIn0.ready := 0.B
  io.axiRaIn1.ready := 0.B

  when(io.axiRaIn1.valid) {
    io.axiRaIn1 <> io.axiRaOut
  }.otherwise {
    io.axiRaIn0 <> io.axiRaOut
  }

  io.axiRdIn0.bits.id   := 0xf.U
  io.axiRdIn1.bits.id   := 0xf.U
  io.axiRdIn0.bits.data := 0.U
  io.axiRdIn1.bits.data := 0.U
  io.axiRdIn0.bits.resp := 0.U
  io.axiRdIn1.bits.resp := 0.U
  io.axiRdIn0.bits.last := 0.B
  io.axiRdIn1.bits.last := 0.B
  io.axiRdIn0.bits.user := 0.U
  io.axiRdIn1.bits.user := 0.U
  io.axiRdIn0.valid     := 0.B
  io.axiRdIn1.valid     := 0.B

  when(io.axiRdOut.valid && io.axiRdIn1.ready && io.axiRdOut.bits.id === 1.U) {
    io.axiRdIn1 <> io.axiRdOut
  }.otherwise {
    io.axiRdIn0 <> io.axiRdOut

    when(!io.axiRdIn0.ready || (RID_FIFO.io.count =/= 0.U)) {
      io.axiRdOut.ready := RID_FIFO.io.enq.ready

      RID_FIFO.io.enq.bits   := io.axiRdOut.bits.id
      RDATA_FIFO.io.enq.bits := io.axiRdOut.bits.data
      RRESP_FIFO.io.enq.bits := io.axiRdOut.bits.resp
      RLAST_FIFO.io.enq.bits := io.axiRdOut.bits.last
      RUSER_FIFO.io.enq.bits := io.axiRdOut.bits.user

      RID_FIFO.io.enq.valid   := io.axiRdOut.valid
      RDATA_FIFO.io.enq.valid := io.axiRdOut.valid
      RRESP_FIFO.io.enq.valid := io.axiRdOut.valid
      RLAST_FIFO.io.enq.valid := io.axiRdOut.valid
      RUSER_FIFO.io.enq.valid := io.axiRdOut.valid
    }

    when(RID_FIFO.io.count =/= 0.U) {
      io.axiRdIn0.bits.id   := RID_FIFO.io.deq.bits
      io.axiRdIn0.bits.data := RDATA_FIFO.io.deq.bits
      io.axiRdIn0.bits.resp := RRESP_FIFO.io.deq.bits
      io.axiRdIn0.bits.last := RLAST_FIFO.io.deq.bits
      io.axiRdIn0.bits.user := RUSER_FIFO.io.deq.bits
      io.axiRdIn0.valid     := RID_FIFO.io.deq.valid

      RID_FIFO.io.deq.ready   := io.axiRdIn0.ready
      RDATA_FIFO.io.deq.ready := io.axiRdIn0.ready
      RRESP_FIFO.io.deq.ready := io.axiRdIn0.ready
      RLAST_FIFO.io.deq.ready := io.axiRdIn0.ready
      RUSER_FIFO.io.deq.ready := io.axiRdIn0.ready
    }
  }

  when(pending === 2.U) {
    io.axiRaIn0.ready := 0.B
    when(io.axiRdIn0.fire) {
      pending := pending - 1.U
    }
  }.otherwise {
    when(io.axiRdIn0.fire && !(io.axiRaIn0.fire)) {
      pending := pending - 1.U
    }.elsewhen(!(io.axiRdIn0.fire) && io.axiRaIn0.fire) {
      pending := pending + 1.U + io.axiRaIn0.bits.len
    }
  }
}

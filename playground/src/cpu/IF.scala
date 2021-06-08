package cpu

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._

// instruction fetching module
class IF extends Module {
  val io = IO(new Bundle {
    val axiRa  = new AXIra            // connected
    val axiRd  = new AXIrd            // connected
    val lastVR = new LastVR           // connected
    val nextVR = Flipped(new LastVR)  // connected
    val pcIo   = Flipped(new PCIO)    // connected
    val instr  = Output(UInt(XLEN.W)) // connected
  })

  io.axiRa.ARID     := 0.U // 0 for IF
  io.axiRa.ARLEN    := 0.U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.axiRa.ARSIZE   := 5.U // 2^(ARSIZE) data bit width per AXI Transfer
  io.axiRa.ARBURST  := 1.U // 1 for INCR type
  io.axiRa.ARLOCK   := 0.U // since we do not use it yet
  io.axiRa.ARCACHE  := 0.U // since we do not use it yet
  io.axiRa.ARPROT   := 0.U // since we do not use it yet
  io.axiRa.ARADDR   := io.pcIo.rdata
  io.axiRa.ARQOS    := DontCare
  io.axiRa.ARUSER   := DontCare
  io.axiRa.ARREGION := DontCare
  io.pcIo.wen       := 0.B
  io.pcIo.wdata     := 0.U

  val ARVALID = RegInit(0.B)
  val NVALID  = RegInit(0.B)
  val LREADY  = RegInit(1.B)
  val RREADY  = RegInit(0.B)
  val instr   = RegInit(0.U(32.W))

  io.axiRa.ARVALID := ARVALID
  io.nextVR.VALID  := NVALID
  io.axiRd.RREADY  := RREADY
  io.lastVR.READY  := LREADY
  io.instr         := instr

  // FSM
  when(io.nextVR.VALID && io.nextVR.READY) { // ready to trans instr to the next level
    NVALID  := 0.B
    LREADY  := 1.B
  }.elsewhen(io.axiRd.RVALID && io.axiRd.RREADY) { // ready to receive instr from BUS
    when(io.axiRd.RID === 0.U) { // remember to check the transaction ID
      RREADY := 0.B
      NVALID := 1.B
      instr  := io.axiRd.RDATA
    }
  }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) { // ready to send request to BUS
    ARVALID := 0.B
    RREADY  := 1.B
  }.elsewhen(io.lastVR.VALID && io.lastVR.READY) { // ready to start fetching instr
    LREADY  := 0.B
    ARVALID := 1.B
  }

  if (debugIO & false) {
    printf("if_last_ready = %d\n", io.lastVR.READY)
    printf("if_last_valid = %d\n", io.lastVR.VALID)
    printf("if_next_ready = %d\n", io.nextVR.READY)
    printf("if_next_valid = %d\n", io.nextVR.VALID)
    printf("io.instr      = %x\n", io.instr       )
  }
}

package cpu

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

// instruction fetching module
class IF extends RawModule {
  val io = IO(new Bundle {
    val ifBasic  = new BASIC            // connected
    val ifAxiRa  = new AXIra            // connected
    val ifAxiRd  = new AXIrd            // connected
    val ifLastVR = new LastVR           // connected
    val ifNextVR = Flipped(new LastVR)  // connected
    val ifPcIo   = new PCIO             // connected
    val instr    = Output(UInt(XLEN.W)) // connected
  })
  
  io.ifAxiRa.ARID     := 0.U // 0 for IF
  io.ifAxiRa.ARLEN    := 0.U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.ifAxiRa.ARSIZE   := 6.U // 2^(ARSIZE) data bit width per AXI Transfer
  io.ifAxiRa.ARBURST  := 1.U // 1 for INCR type
  io.ifAxiRa.ARLOCK   := 0.U // since we do not use it yet
  io.ifAxiRa.ARCACHE  := 0.U // since we do not use it yet
  io.ifAxiRa.ARPROT   := 0.U // since we do not use it yet
  io.ifAxiRa.ARQOS    := DontCare
  io.ifAxiRa.ARUSER   := DontCare
  io.ifAxiRa.ARREGION := DontCare
  io.ifPcIo.wen       := 0.B
  io.ifPcIo.wdata     := 0.U

  // Clk: Posedge trigger; Rst: Low level effective
  withClockAndReset(io.ifBasic.ACLK, ~io.ifBasic.ARESETn) {
    val ARVALID = RegInit(0.B)
    val NVALID  = RegInit(0.B)
    val LREADY  = RegInit(0.B)
    val RREADY  = RegInit(0.B)
    val instr   = RegInit(0.U(XLEN.W))

    io.ifAxiRa.ARVALID   := ARVALID
    io.ifNextVR.VALID := NVALID
    io.ifAxiRd.RREADY    := RREADY
    io.ifLastVR.READY := LREADY
    io.instr             := instr
    io.ifAxiRa.ARADDR    := io.ifPcIo.rdata

    when(io.ifNextVR.VALID && io.ifNextVR.READY) { // ready to trans instr to the next level
      NVALID  := 0.B
      LREADY  := 1.B
    }.elsewhen(io.ifAxiRd.RVALID && io.ifAxiRd.RREADY) { // ready to receive instr from BUS
      when(io.ifAxiRd.RID === 0.U) { // remember to check the transaction ID
        RREADY := 0.B
        NVALID := 1.B
        instr  := io.ifAxiRd.RDATA
      }
    }.elsewhen(io.ifAxiRa.ARVALID && io.ifAxiRa.ARREADY) { // ready to send request to BUS
      ARVALID := 0.B
      RREADY  := 1.B
    }.elsewhen(io.ifLastVR.VALID && io.ifLastVR.READY) { // ready to start fetching instr
      LREADY  := 0.B
      ARVALID := 1.B
    }
  }


}
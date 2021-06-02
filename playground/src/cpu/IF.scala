package cpu

import chisel3._
import chisel3.util._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._

trait DataNext {
  val NDATA = Output(UInt(XLEN.W))
}

// instruction fetching module
class IF extends RawModule {
  val io = IO(new  cpu.axi.AXI   with cpu.axi.AXIra 
              with cpu.axi.AXIrd with cpu.axi.LastNext with DataNext)
  
  io.ARID    := 0.U // 0 for IF
  io.ARLEN   := 0.U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.ARSIZE  := 6.U // 2^(ARSIZE) data bit width per AXI Transfer
  io.ARBURST := 1.U // 1 for INCR type
  io.ARLOCK  := 0.U // since we do not use it yet
  io.ARCACHE := 0.U // since we do not use it yet
  io.ARPROT  := 0.U // since we do not use it yet

  // Clk: Posedge trigger; Rst: Low level effectiveki
  withClockAndReset(io.ACLK, ~io.ARESETn) {
    val ARVALID = RegInit(0.B)
    val NVALID  = RegInit(0.B)
    val LREADY  = RegInit(0.B)
    val RREADY  = RegInit(0.B)
    val NDATA   = RegInit(0.U(XLEN.W))

    io.ARVALID := ARVALID
    io.NVALID  := NVALID
    io.RREADY  := RREADY
    io.LREADY  := LREADY
    io.NDATA   := NDATA
    io.ARADDR  := PC.io.rdata

    when(io.NVALID && io.NREADY) { // ready to trans instr to the next level
      NVALID  := 0.B
      LREADY  := 1.B
    }.elsewhen(io.RVALID && io.RREADY) { // ready to receive instr from BUS
      when(io.RID === 0.U) { // remember to check the transaction ID
        RREADY := 0.B
        NVALID := 1.B
        NDATA  := io.RDATA
      }
    }.elsewhen(io.ARVALID && io.ARREADY) { // ready to send request to BUS
      ARVALID := 0.B
      RREADY  := 1.B
    }.elsewhen(io.LVALID && io.LREADY) { // ready to start fetching instr
      LREADY  := 0.B
      ARVALID := 1.B
    }
  }


  // ???
}
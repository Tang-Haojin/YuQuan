package cpu

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._

class IFOutput extends Bundle {
  val instr = Output(UInt(32.W))
  val pc    = Output(UInt(XLEN.W))
}

class IF extends Module {
  val io = IO(new Bundle {
    val axiRa  = new AXIra
    val axiRd  = new AXIrd
    val nextVR = Flipped(new LastVR)
    val pcIo   = Flipped(new PCIO)
    val output = new IFOutput
    val jmpBch = Input (Bool())
    val jbAddr = Input (UInt(XLEN.W))
  })

  val running::blocking::Nil = Enum(2)
  val state = RegInit(0.U(1.W))

  val pendingNum = RegInit(0.U(XLEN.W))
  val basePC     = RegInit(MEMBase.U(XLEN.W))
  val NVALID     = RegInit(0.B)

  io.axiRa.ARID     := 0.U // 0 for IF
  io.axiRa.ARLEN    := 0.U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  io.axiRa.ARSIZE   := 5.U // 2^(ARSIZE) data bit width per AXI Transfer
  io.axiRa.ARBURST  := 1.U // 1 for INCR type
  io.axiRa.ARLOCK   := 0.U // since we do not use it yet
  io.axiRa.ARCACHE  := 0.U // since we do not use it yet
  io.axiRa.ARPROT   := 0.U // since we do not use it yet
  io.axiRa.ARADDR   := basePC
  io.axiRa.ARQOS    := DontCare
  io.axiRa.ARUSER   := DontCare
  io.axiRa.ARREGION := DontCare
  io.pcIo.wen       := 0.B
  io.pcIo.wdata     := 0.U

  val instr   = RegInit(0x00000013.U(32.W))
  val pc      = RegInit(0.U(XLEN.W))

  io.axiRa.ARVALID := 0.B
  io.nextVR.VALID  := NVALID
  io.axiRd.RREADY  := 1.B
  io.output.instr  := instr
  io.output.pc     := pc

  switch(state) {
    is(running) {
      when(!io.jmpBch) {
        when(io.nextVR.READY) {
          io.axiRa.ARVALID := 1.B
          when(io.axiRa.ARVALID && io.axiRa.ARREADY) {
            basePC    := basePC + 4.U
          }
          when(io.axiRd.RREADY && io.axiRd.RVALID && (io.axiRd.RID === 0.U)) { // remember to check the transaction ID
            io.pcIo.wen   := 1.B
            instr         := io.axiRd.RDATA
            pc            := io.pcIo.rdata
            io.pcIo.wdata := io.pcIo.rdata + 4.U
            NVALID := 1.B
          }.otherwise {
            NVALID := 0.B
          }
        }.otherwise {
          io.axiRd.RREADY := 0.B
        }
      }.otherwise {
        when(io.nextVR.READY) {
          state  := blocking
          basePC := io.jbAddr
          io.axiRa.ARVALID := 0.B
          NVALID := 0.B
          instr := 0x00000013.U // nop
          io.pcIo.wen   := 1.B
          io.pcIo.wdata := io.jbAddr
          when(io.axiRd.RVALID && (io.axiRd.RID === 0.U) && (pendingNum === 1.U)) {
            state := running
          }
        }.otherwise {
          io.axiRd.RREADY := 0.B
        }
      }
    }
    is(blocking) {
      io.axiRa.ARVALID := 0.B
      NVALID := 0.B
      when(io.axiRd.RVALID && (io.axiRd.RID === 0.U)) {
        when(pendingNum === 1.U) {
          state := running
        }
      }
      when(pendingNum === 0.U) {
        state := running
      }
    }
  }

  when(io.axiRa.ARREADY && io.axiRa.ARVALID && !(io.axiRd.RREADY && io.axiRd.RVALID)) {
    pendingNum := pendingNum + 1.U
  }.elsewhen(!(io.axiRa.ARREADY && io.axiRa.ARVALID) && io.axiRd.RREADY && io.axiRd.RVALID) {
    pendingNum := pendingNum - 1.U
  }

  if (debugIO) {
    printf("if_next_ready   = %d\n", io.nextVR.READY)
    printf("if_next_valid   = %d\n", io.nextVR.VALID)
    printf("io.output.instr = %x\n", io.output.instr)
    printf("io.output.pc    = %x\n", io.output.pc   )
    printf("pendingNum      = %x\n", pendingNum     )
    printf("state           = %x\n", state          )
    printf("io.jmpBch       = %x\n", io.jmpBch      )
  }
}

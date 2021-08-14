package cpu.pipeline

import chisel3._

import tools._
import cpu.config.GeneralConfig._
import cpu.config.Debug._
import cpu.cache._

class IF extends Module {
  val io = IO(new Bundle {
    val icache = Flipped(new CpuIO)
    val nextVR = Flipped(new LastVR)
    val output = new IFOutput
    val jmpBch = Input (Bool())
    val jbAddr = Input (UInt(XLEN.W))
  })

  val instr  = RegInit(0x00000013.U(32.W))
  val pc     = RegInit(MEMBase.U(XLEN.W))
  val NVALID = RegInit(0.B)

  val wireInstr  = WireDefault(UInt(32.W), instr)
  val wirePC     = WireDefault(UInt(XLEN.W), pc - 4.U)
  val wireNVALID = WireDefault(Bool(), NVALID)
  val wireNewPC  = WireDefault(UInt(XLEN.W), pc)

  io.nextVR.VALID := wireNVALID

  io.output.instr := wireInstr
  io.output.pc    := wirePC

  io.icache.cpuReq.data  := DontCare
  io.icache.cpuReq.rw    := DontCare
  io.icache.cpuReq.valid := io.nextVR.READY
  io.icache.cpuReq.addr  := wireNewPC

  when(io.icache.cpuResult.ready) {
    instr      := io.icache.cpuResult.data
    wireInstr  := io.icache.cpuResult.data
    pc         := pc + 4.U
    wirePC     := pc
    wireNewPC  := pc + 4.U
    wireNVALID := 1.B
    when(!io.nextVR.READY) {
      NVALID := 1.B
    }
    when(io.jmpBch && io.nextVR.READY && io.nextVR.VALID) {
      wireNewPC := io.jbAddr
      pc        := io.jbAddr
    }
  }.elsewhen(io.jmpBch && io.nextVR.READY && io.nextVR.VALID) {
    wireNewPC := io.jbAddr
    pc        := io.jbAddr
  }

  when(io.nextVR.READY && io.nextVR.VALID) {
    NVALID := 0.B
  }

  if (debugIO) {
    printf("if_next_ready   = %d\n", io.nextVR.READY)
    printf("if_next_valid   = %d\n", io.nextVR.VALID)
    printf("io.output.instr = %x\n", io.output.instr)
    printf("io.output.pc    = %x\n", io.output.pc   )
    printf("io.jmpBch       = %x\n", io.jmpBch      )
  }
}

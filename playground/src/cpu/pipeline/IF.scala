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
    val jmpBch = Input(Bool())
    val jbAddr = Input(UInt(XLEN.W))
  })

  val instr  = RegInit(0x00000013.U(32.W))
  val pc     = RegInit(MEMBase.U(XLEN.W))
  val NVALID = RegInit(0.B)

  val wireInstr  = WireDefault(UInt(32.W), instr);      io.output.instr := wireInstr
  val wirePC     = WireDefault(UInt(XLEN.W), pc - 4.U); io.output.pc    := wirePC
  val wireNVALID = WireDefault(Bool(), NVALID);         io.nextVR.VALID := wireNVALID
  val wireNewPC  = WireDefault(UInt(XLEN.W), pc)

  io.icache.cpuReq.data  := DontCare
  io.icache.cpuReq.rw    := DontCare
  io.icache.cpuReq.wmask := DontCare
  io.icache.cpuReq.valid := io.nextVR.READY
  io.icache.cpuReq.addr  := wireNewPC

  when(io.icache.cpuResult.ready) {
    instr      := io.icache.cpuResult.data
    wireInstr  := io.icache.cpuResult.data
    pc         := pc + 4.U
    wirePC     := pc
    wireNewPC  := pc + 4.U
    wireNVALID := 1.B
    when(!io.icache.cpuReq.valid) { NVALID := 1.B }
  }

  when(io.jmpBch && io.nextVR.READY && io.nextVR.VALID) {
    wireNewPC := io.jbAddr
    pc        := io.jbAddr
  }

  when(io.nextVR.READY && io.nextVR.VALID && !io.icache.cpuResult.ready) {
    NVALID := 0.B
  }
}

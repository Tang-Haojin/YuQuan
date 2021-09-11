package cpu.pipeline

import chisel3._
import chipsalliance.rocketchip.config._

import utils._
import cpu.cache._
import cpu.tools._

class IF(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val icache = Flipped(new CpuIO(32))
    val nextVR = Flipped(new LastVR)
    val output = new IFOutput
    val jmpBch = Input(Bool())
    val jbAddr = Input(UInt(alen.W))
  })

  private val MEMBase = if (UseFlash) SPIFLASH.BASE else DRAM.BASE
  private val instr   = RegInit(0x00000013.U(32.W))
  private val pc      = RegInit(MEMBase.U(alen.W))
  private val NVALID  = RegInit(0.B)

  private val wireInstr  = WireDefault(UInt(32.W), instr);      io.output.instr := wireInstr
  private val wirePC     = WireDefault(UInt(alen.W), pc - 4.U); io.output.pc    := wirePC
  private val wireNVALID = WireDefault(Bool(), NVALID);         io.nextVR.VALID := wireNVALID
  private val wireNewPC  = WireDefault(UInt(alen.W), pc)

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

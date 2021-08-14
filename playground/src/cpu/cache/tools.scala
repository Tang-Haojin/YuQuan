package cpu.cache

import chisel3._
import chisel3.util._
import cpu.config.GeneralConfig._
import tools.AxiMasterChannel

// CPU -> Cache Controller
class CpuReq extends Bundle {
  val addr  = Input(UInt(XLEN.W))
  val data  = Input(UInt(XLEN.W))
  val rw    = Input(Bool())
  val valid = Input(Bool())
}

// Cache Controller -> CPU
class CpuResult extends Bundle {
  val data  = Output(UInt(XLEN.W))
  val ready = Output(Bool())
}

// CPU <-> Cache Controller
class CpuIO extends Bundle {
  val cpuReq    = new CpuReq
  val cpuResult = new CpuResult
}

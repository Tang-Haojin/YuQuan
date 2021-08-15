package cpu.cache

import chisel3._
import chisel3.util._
import cpu.config.CacheConfig.DCache._
import cpu.config.GeneralConfig._
import tools.AxiMasterChannel

// CPU -> Cache Controller
class CpuReq extends Bundle {
  val addr  = Input(UInt(XLEN.W))
  val data  = Input(UInt(XLEN.W))
  val rw    = Input(Bool())
  val wmask = Input(UInt((XLEN / 8).W))
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

class IsPeripheral(addr: UInt) {
  val isPeripheral = WireDefault(0.B)
  when((addr >= UART0_MMIO.BASE.U) && (addr < (UART0_MMIO.BASE + UART0_MMIO.SIZE).U)) {
    isPeripheral := 1.B
  }
  when((addr >= CLINT.CLINT.U) && (addr < (CLINT.CLINT + CLINT.CLINT_SIZE).U)) {
    isPeripheral := 1.B
  }
  when((addr >= PLIC.PLIC.U) && (addr < (PLIC.PLIC + PLIC.PLIC_SIZE).U)) {
    isPeripheral := 1.B
  }
}

object IsPeripheral {
  /** Construct an [[IsPeripheral]]
   * @param addr The address that to be examined
   */
  def apply(addr: UInt): IsPeripheral = new IsPeripheral(addr)

  import scala.language.implicitConversions
  implicit def getResult(x: IsPeripheral): Bool = x.isPeripheral
}

class WbBuffer(memIO: AxiMasterChannel, sendData: UInt, sendAddr: UInt) {
  val used   = RegInit(0.B)
  val buffer = RegInit(0.U((BlockSize * 8).W))
  val ready  = RegInit(1.B)
  val valid  = WireDefault(0.B)
  val wbAddr = RegInit(0.U(ALEN.W))
  private val AWVALID = RegInit(0.B); memIO.axiWa.AWVALID := AWVALID
  private val WVALID  = RegInit(0.B); memIO.axiWd.WVALID  := WVALID
  private val BREADY  = RegInit(0.B); memIO.axiWr.BREADY  := BREADY
  private val sent    = RegInit(0.U(LogBurstLen.W))

  private val wireWdata = WireDefault(0.U(XLEN.W))
  private val wdata = WireDefault(VecInit((0 until BlockSize / 8).map { i =>
    buffer(i * 64 + 63, i * 64)
  }))
  memIO.axiWa.AWID     := 1.U // 1 for MEM
  memIO.axiWa.AWLEN    := (BurstLen - 1).U // (AWLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  memIO.axiWa.AWSIZE   := AxSIZE.U // 2^(AWSIZE) bytes per AXI Transfer
  memIO.axiWa.AWBURST  := 1.U // 1 for INCR type
  memIO.axiWa.AWLOCK   := 0.U // since we do not use it yet
  memIO.axiWa.AWCACHE  := 0.U // since we do not use it yet
  memIO.axiWa.AWPROT   := 0.U // since we do not use it yet
  memIO.axiWa.AWQOS    := DontCare
  memIO.axiWa.AWUSER   := DontCare
  memIO.axiWa.AWREGION := DontCare
  memIO.axiWa.AWADDR   := wbAddr
  memIO.axiWd.WDATA := wireWdata
  memIO.axiWd.WLAST := 0.B
  memIO.axiWd.WSTRB := Fill(XLEN / 8, 1.B)
  memIO.axiWd.WUSER := DontCare
  when(ready) {
    when(valid) {
      used    := 1.B
      ready   := 0.B
      AWVALID := 1.B
      WVALID  := 1.B
      buffer  := sendData
      wbAddr  := sendAddr
    }
  }.otherwise {
    when(memIO.axiWa.AWREADY && memIO.axiWa.AWVALID) {
      AWVALID := 0.B
      when(!WVALID) { BREADY := 1.B }
    }
    when(memIO.axiWd.WREADY && memIO.axiWd.WVALID) {
      wireWdata := wdata(sent)
      when(sent === (BurstLen - 1).U) {
        sent   := 0.U
        WVALID := 0.B
        BREADY := 1.B
        memIO.axiWd.WLAST := 1.B
      }.otherwise { sent := sent + 1.U }
    }
    when(memIO.axiWr.BREADY && memIO.axiWr.BVALID) {
      ready  := 1.B
      BREADY := 0.B
    }
  }
}

object WbBuffer {
  /** Construct a [[WbBuffer]]
   * @param memIO An [[AxiMasterChannel]] IO interface
   * @param sendData Data to be sent
   * @param sendAddr Address to send to
   */
  def apply(memIO: AxiMasterChannel, sendData: UInt, sendAddr: UInt): WbBuffer = new WbBuffer(memIO, sendData, sendAddr)
}

class PassThrough(memIO: AxiMasterChannel, wbFree: Bool, addr: UInt, wdata: UInt, wstrb: UInt, rw: Bool) {
  val ready  = RegInit(1.B)
  val valid  = WireDefault(0.B)
  val finish = WireDefault(0.B)

  private val AWVALID = RegInit(1.B)
  private val WVALID  = RegInit(1.B)
  private val BREADY  = RegInit(0.B)

  private val ARVALID = RegInit(1.B)
  private val RREADY  = RegInit(0.B)

  private val regRdata = RegInit(0.U(XLEN.W))
  val rdata = WireDefault(UInt(XLEN.W), regRdata)

  when((ready && valid) || !ready) {
    ready := 0.B
    when(rw) {
      when(wbFree) {
        memIO.axiWa.AWVALID := AWVALID
        memIO.axiWa.AWLEN   := 0.U
        memIO.axiWa.AWSIZE  := AxSIZE.U
        memIO.axiWa.AWADDR  := addr

        memIO.axiWd.WVALID  := WVALID
        memIO.axiWd.WLAST   := 1.B
        memIO.axiWd.WDATA   := wdata
        memIO.axiWd.WSTRB   := wstrb

        memIO.axiWr.BREADY  := BREADY

        when(memIO.axiWa.AWREADY && memIO.axiWa.AWVALID) {
          AWVALID := 0.B
          when(!WVALID) { BREADY := 1.B }
        }
        when(memIO.axiWd.WREADY && memIO.axiWd.WVALID) {
          WVALID := 0.B
          when(!AWVALID || (memIO.axiWa.AWREADY && memIO.axiWa.AWVALID)) { BREADY := 1.B }
        }
        when(memIO.axiWr.BREADY && memIO.axiWr.BVALID) {
          ready   := 1.B
          finish  := 1.B
          AWVALID := 1.B
          WVALID  := 1.B
          BREADY  := 0.B
        }
      }
    }.otherwise {
      memIO.axiRa.ARVALID := ARVALID
      memIO.axiRa.ARLEN   := 0.U
      memIO.axiRa.ARSIZE  := AxSIZE.U
      memIO.axiRa.ARADDR  := addr

      memIO.axiRd.RREADY  := RREADY

      when(memIO.axiRa.ARREADY && memIO.axiRa.ARVALID) {
        ARVALID := 0.B
        RREADY  := 1.B
      }
      when(memIO.axiRd.RREADY && memIO.axiRd.RVALID) {
        ready    := 1.B
        finish   := 1.B
        ARVALID  := 1.B
        RREADY   := 0.B
        rdata    := memIO.axiRd.RDATA
        regRdata := rdata
      }
    }
  }
}

object PassThrough {
  /** Construct a [[PassThrough]]
   * @param memIO An [[AxiMasterChannel]] IO interface
   * @param wbFree `ready` bit of [[WbBuffer]]
   * @param addr Address to read or write
   * @param wdata Data to be sent
   * @param wstrb Write data byte mask
   * @param rw Read or Write request
   */
  def apply(memIO: AxiMasterChannel, wbFree: Bool, addr: UInt, wdata: UInt, wstrb: UInt, rw: Bool): PassThrough = new PassThrough(memIO, wbFree, addr, wdata, wstrb, rw)
}

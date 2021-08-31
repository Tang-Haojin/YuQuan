package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._
import cpu._

// CPU -> Cache Controller
class CpuReq(implicit p: Parameters) extends YQBundle {
  val addr  = Input(UInt(xlen.W))
  val data  = Input(UInt(xlen.W))
  val rw    = Input(Bool())
  val wmask = Input(UInt((xlen / 8).W))
  val valid = Input(Bool())
}

// Cache Controller -> CPU
class CpuResult(implicit p: Parameters) extends YQBundle {
  val data  = Output(UInt(xlen.W))
  val ready = Output(Bool())
}

// CPU <-> Cache Controller
class CpuIO(implicit p: Parameters) extends YQBundle {
  val cpuReq    = new CpuReq
  val cpuResult = new CpuResult
}

class IsPeripheral(addr: UInt)(implicit p: Parameters) {
  private val DRAM = p(DRAM_MMAP)
  val isPeripheral = WireDefault(0.B)
  when((addr < DRAM.BASE.U) || (addr >= (DRAM.BASE + DRAM.SIZE).U)) {
    isPeripheral := 1.B
  }
}

object IsPeripheral {
  /** Construct an [[IsPeripheral]]
   * @param addr The address that to be examined
   */
  def apply(addr: UInt)(implicit p: Parameters): IsPeripheral = new IsPeripheral(addr)

  import scala.language.implicitConversions
  implicit def getResult(x: IsPeripheral): Bool = x.isPeripheral
}

class WbBuffer(memIO: AxiMasterChannel, sendData: UInt, sendAddr: UInt)(implicit val p: Parameters) extends CPUParams with CacheParams {
  val used   = RegInit(0.B)
  val buffer = RegInit(0.U((BlockSize * 8).W))
  val ready  = RegInit(1.B)
  val valid  = WireDefault(0.B)
  val wbAddr = RegInit(0.U(alen.W))
  private val AWVALID = RegInit(0.B); memIO.axiWa.AWVALID := AWVALID
  private val WVALID  = RegInit(0.B); memIO.axiWd.WVALID  := WVALID
  private val BREADY  = RegInit(0.B); memIO.axiWr.BREADY  := BREADY
  private val sent    = RegInit(0.U(LogBurstLen.W))

  private val wireWdata = WireDefault(0.U(xlen.W))
  private val wdata = WireDefault(VecInit((0 until BlockSize / 8).map { i =>
    buffer(i * 64 + 63, i * 64)
  }))
  memIO.axiWa.AWID     := 1.U // 1 for MEM
  memIO.axiWa.AWLEN    := (BurstLen - 1).U // (AWLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  memIO.axiWa.AWSIZE   := axSize.U // 2^(AWSIZE) bytes per AXI Transfer
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
  memIO.axiWd.WSTRB := Fill(xlen / 8, 1.B)
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
  def apply(memIO: AxiMasterChannel, sendData: UInt, sendAddr: UInt)(implicit p: Parameters): WbBuffer = new WbBuffer(memIO, sendData, sendAddr)
}

class PassThrough(readonly: Boolean)(var memIO: AxiMasterChannel, wbFree: Bool, addr: UInt, wdata: UInt, wstrb: UInt, var rw: Bool)(implicit val p: Parameters) extends CPUParams {
  val ready  = RegInit(1.B)
  val valid  = WireDefault(0.B)
  val finish = WireDefault(0.B)

  private val ARVALID = RegInit(1.B)
  private val RREADY  = RegInit(0.B)

  private val regRdata = RegInit(0.U(xlen.W))
  val rdata = WireDefault(UInt(xlen.W), regRdata)

  if (readonly) rw = 0.B

  when((ready && valid) || !ready) {
    ready := 0.B
    if (!readonly) {
      val AWVALID = RegInit(1.B)
      val WVALID  = RegInit(1.B)
      val BREADY  = RegInit(0.B)
      when(rw) {
        when(wbFree) {
          memIO.axiWa.AWVALID := AWVALID
          memIO.axiWa.AWLEN   := 0.U
          memIO.axiWa.AWSIZE  := axSize.U
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
      }
    }
    when(!rw) {
      memIO.axiRa.ARVALID := ARVALID
      memIO.axiRa.ARLEN   := 0.U
      memIO.axiRa.ARSIZE  := axSize.U
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
   * @param readonly Whether the [[PassThrough]] readonly
   * @param memIO An [[AxiMasterChannel]] IO interface
   * @param wbFree `ready` bit of [[WbBuffer]]
   * @param addr Address to read or write
   * @param wdata Data to be sent
   * @param wstrb Write data byte mask
   * @param rw Read or Write request
   */
  def apply(readonly: Boolean)(memIO: AxiMasterChannel, wbFree: Bool, addr: UInt, wdata: UInt, wstrb: UInt, rw: Bool)(implicit p: Parameters): PassThrough = new PassThrough(readonly)(memIO, wbFree, addr, wdata, wstrb, rw)
}

class ICacheMemIODefault(memIO: AxiMasterChannel, arValid: Bool, arAddr: UInt, rReady: Bool)(implicit val p: Parameters) extends CPUParams with CacheParams {
  memIO.axiRa.ARID     := 0.U // 0 for IF
  memIO.axiRa.ARLEN    := (BurstLen - 1).U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  memIO.axiRa.ARSIZE   := axSize.U // 2^(ARSIZE) bytes per AXI Transfer
  memIO.axiRa.ARBURST  := 1.U // 1 for INCR type
  memIO.axiRa.ARLOCK   := 0.U // since we do not use it yet
  memIO.axiRa.ARCACHE  := 0.U // since we do not use it yet
  memIO.axiRa.ARPROT   := 0.U // since we do not use it yet
  memIO.axiRa.ARQOS    := DontCare
  memIO.axiRa.ARUSER   := DontCare
  memIO.axiRa.ARREGION := DontCare
  memIO.axiRa.ARVALID  := arValid
  memIO.axiRa.ARADDR   := arAddr

  memIO.axiRd.RREADY := rReady

  memIO.axiWa := DontCare
  memIO.axiWd := DontCare
  memIO.axiWr := DontCare
}

object ICacheMemIODefault {
  /** Construct an [[ICacheMemIODefault]]
   * @param memIO An [[AxiMasterChannel]] IO interface
   * @param arValid Default `ARVALID` for AXI
   * @param arAddr Default `ARADDR` for AXI
   * @param rReady Default `RREADY` for AXI
   */
  def apply(memIO: AxiMasterChannel, arValid: Bool, arAddr: UInt, rReady: Bool)(implicit p: Parameters): ICacheMemIODefault = new ICacheMemIODefault(memIO, arValid, arAddr, rReady)
}

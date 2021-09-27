package cpu.cache

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._
import cpu._

// CPU -> Cache Controller
class CpuReq(implicit p: Parameters) extends YQBundle {
  val addr  = UInt(alen.W)
  val data  = UInt(xlen.W)
  val size  = UInt(3.W)
  val rw    = Bool()
  val wmask = UInt((xlen / 8).W)
  val valid = Bool()
}

// Cache Controller -> CPU
class CpuResult(datalen: Int = 64)(implicit p: Parameters) extends YQBundle {
  val data  = UInt(datalen.W)
  val ready = Bool()
}

// CPU <-> Cache Controller
class CpuIO(datalen: Int = 64)(implicit p: Parameters) extends YQBundle {
  val cpuReq    = Input (new CpuReq)
  val cpuResult = Output(new CpuResult(datalen))
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

class WbBuffer(memIO: AXI_BUNDLE, sendData: UInt, sendAddr: UInt)(implicit val p: Parameters) extends CPUParams with CacheParams {
  val used   = RegInit(0.B)
  val buffer = RegInit(0.U((BlockSize * 8).W))
  val ready  = RegInit(1.B)
  val valid  = WireDefault(0.B)
  val wbAddr = RegInit(0.U(alen.W))
  private val AWVALID = RegInit(0.B); memIO.aw.valid := AWVALID
  private val WVALID  = RegInit(0.B); memIO.w .valid := WVALID
  private val BREADY  = RegInit(0.B); memIO.b .ready := BREADY
  private val sent    = RegInit(0.U(LogBurstLen.W))

  private val wireWdata = WireDefault(0.U(xlen.W))
  private val wdata = WireDefault(VecInit((0 until BlockSize / 8).map { i =>
    buffer(i * 64 + 63, i * 64)
  }))
  memIO.aw.bits.id     := 1.U // 1 for MEM
  memIO.aw.bits.len    := (BurstLen - 1).U // (AWLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  memIO.aw.bits.size   := axSize.U // 2^(AWSIZE) bytes per AXI Transfer
  memIO.aw.bits.burst  := 1.U // 1 for INCR type
  memIO.aw.bits.lock   := 0.U // since we do not use it yet
  memIO.aw.bits.cache  := 0.U // since we do not use it yet
  memIO.aw.bits.prot   := 0.U // since we do not use it yet
  memIO.aw.bits.qos    := DontCare
  memIO.aw.bits.user   := DontCare
  memIO.aw.bits.region := DontCare
  memIO.aw.bits.addr   := wbAddr
  memIO.w .bits.data   := wireWdata
  memIO.w .bits.last   := 0.B
  memIO.w .bits.strb   := Fill(xlen / 8, 1.B)
  memIO.w .bits.user   := DontCare
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
    when(memIO.aw.fire) {
      AWVALID := 0.B
      when(!WVALID) { BREADY := 1.B }
    }
    when(memIO.w.fire) {
      wireWdata := wdata(sent)
      when(sent === (BurstLen - 1).U) {
        sent   := 0.U
        WVALID := 0.B
        BREADY := 1.B
        memIO.w.bits.last := 1.B
      }.otherwise { sent := sent + 1.U }
    }
    when(memIO.b.fire) {
      ready  := 1.B
      BREADY := 0.B
    }
  }
}

object WbBuffer {
  /** Construct a [[WbBuffer]]
   * @param memIO An [[AXI_BUNDLE]] IO interface
   * @param sendData Data to be sent
   * @param sendAddr Address to send to
   */
  def apply(memIO: AXI_BUNDLE, sendData: UInt, sendAddr: UInt)(implicit p: Parameters): WbBuffer = new WbBuffer(memIO, sendData, sendAddr)
}

class PassThrough(readonly: Boolean)(var memIO: AXI_BUNDLE, wbFree: Bool, addr: UInt, wdata: UInt, wstrb: UInt, var rw: Bool, axsize: UInt = log2Ceil(32 / 8).U)(implicit val p: Parameters) extends CPUParams {
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
          memIO.aw.valid     := AWVALID
          memIO.aw.bits.len  := 0.U
          memIO.aw.bits.size := axsize
          memIO.aw.bits.addr := addr

          memIO.w.valid     := WVALID
          memIO.w.bits.last := 1.B
          memIO.w.bits.data := wdata
          memIO.w.bits.strb := wstrb

          memIO.b.ready := BREADY

          when(memIO.aw.fire) {
            AWVALID := 0.B
            when(!WVALID) { BREADY := 1.B }
          }
          when(memIO.w.fire) {
            WVALID := 0.B
            when(!AWVALID || (memIO.aw.fire)) { BREADY := 1.B }
          }
          when(memIO.b.fire) {
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
      memIO.ar.valid     := ARVALID
      memIO.ar.bits.len  := 0.U
      memIO.ar.bits.size := axsize
      memIO.ar.bits.addr := addr

      memIO.r.ready := RREADY

      when(memIO.ar.fire) {
        ARVALID := 0.B
        RREADY  := 1.B
      }
      when(memIO.r.fire) {
        ready    := 1.B
        finish   := 1.B
        ARVALID  := 1.B
        RREADY   := 0.B
        rdata    := memIO.r.bits.data
        regRdata := rdata
      }
    }
  }
}

object PassThrough {
  /** Construct a [[PassThrough]]
   * @param readonly Whether the [[PassThrough]] readonly
   * @param memIO An [[AXI_BUNDLE]] IO interface
   * @param wbFree `ready` bit of [[WbBuffer]]
   * @param addr Address to read or write
   * @param wdata Data to be sent
   * @param wstrb Write data byte mask
   * @param rw Read or Write request
   */
  def apply(readonly: Boolean)(memIO: AXI_BUNDLE, wbFree: Bool, addr: UInt, wdata: UInt, wstrb: UInt, rw: Bool, axsize: UInt = log2Ceil(32 / 8).U)(implicit p: Parameters): PassThrough = new PassThrough(readonly)(memIO, wbFree, addr, wdata, wstrb, rw, axsize)
}

class ICacheMemIODefault(memIO: AXI_BUNDLE, arValid: Bool, arAddr: UInt, rReady: Bool)(implicit val p: Parameters) extends CPUParams with CacheParams {
  memIO.ar.bits.id     := 0.U // 0 for IF
  memIO.ar.bits.len    := (BurstLen - 1).U // (ARLEN + 1) AXI Burst per AXI Transfer (a.k.a. AXI Beat)
  memIO.ar.bits.size   := axSize.U // 2^(ARSIZE) bytes per AXI Transfer
  memIO.ar.bits.burst  := 1.U // 1 for INCR type
  memIO.ar.bits.lock   := 0.U // since we do not use it yet
  memIO.ar.bits.cache  := 0.U // since we do not use it yet
  memIO.ar.bits.prot   := 0.U // since we do not use it yet
  memIO.ar.bits.qos    := DontCare
  memIO.ar.bits.user   := DontCare
  memIO.ar.bits.region := DontCare
  memIO.ar.valid       := arValid
  memIO.ar.bits.addr   := arAddr

  memIO.r.ready := rReady

  memIO.aw := DontCare
  memIO.w  := DontCare
  memIO.b  := DontCare
}

object ICacheMemIODefault {
  /** Construct an [[ICacheMemIODefault]]
   * @param memIO An [[AXI_BUNDLE]] IO interface
   * @param arValid Default `ARVALID` for AXI
   * @param arAddr Default `ARADDR` for AXI
   * @param rReady Default `RREADY` for AXI
   */
  def apply(memIO: AXI_BUNDLE, arValid: Bool, arAddr: UInt, rReady: Bool)(implicit p: Parameters): ICacheMemIODefault = new ICacheMemIODefault(memIO, arValid, arAddr, rReady)
}

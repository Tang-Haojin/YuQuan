package cpu.privileged

import chisel3._
import chisel3.util._


import utils.Convert._
import chipsalliance.rocketchip.config._
import cpu.tools._
import cpu.CPUParams

class CSRsW(implicit p: Parameters) extends YQBundle {
  val wen   = Input(Vec(RegConf.writeCsrsPort, Bool()))
  val wcsr  = Input(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val wdata = Input(Vec(RegConf.writeCsrsPort, UInt(xlen.W)))
}
class CSRsR(implicit p: Parameters) extends YQBundle {
  val rcsr  = Input (Vec(RegConf.readCsrsPort, UInt(12.W)))
  val rdata = Output(Vec(RegConf.readCsrsPort, UInt(xlen.W)))
}

trait CSRsAddr extends CPUParams {
  val Mvendorid     = 0xF11.U
  val Marchid       = 0xF12.U
  val Mimpid        = 0xF13.U
  val Mhartid       = 0xF14.U

  val Mstatus       = 0x300.U
  val Misa          = 0x301.U
  // val Medeleg       = 0x302.U
  // val Mideleg       = 0x303.U
  val Mie           = 0x304.U
  val Mtvec         = 0x305.U
  // val Mcounteren    = 0x306.U

  val Mscratch      = 0x340.U
  val Mepc          = 0x341.U
  val Mcause        = 0x342.U
  val Mtval         = 0x343.U
  val Mip           = 0x344.U

  val Pmpcfg0       = 0x3A0.U
  val Pmpcfg1       = if (xlen == 32) 0x3A1.U else null
  val Pmpcfg2       = 0x3A2.U
  val Pmpcfg3       = if (xlen == 32) 0x3A3.U else null
  val Pmpaddr       = (n: UInt) => 0x3B0.U + n

  val Mcycle        = 0xB00.U
  val Minstret      = 0xB02.U
  val Mhpmcounter   = (n: UInt) => 0xB00.U + n
  val Mcycleh       = if (xlen == 32) 0xB80.U else null
  val Minstreth     = if (xlen == 32) 0xB82.U else null
  val Mhpmcounterh  = if (xlen == 32) (n: UInt) => 0xB80.U + n else null

  val Mcountinhibit = 0x320.U
  val Mhpmevent     = (n: UInt) => 0x320.U + n

  val Mtime         = 0xBFF.U // Customized
  val Mtimecmp      = 0xBFE.U // Customized
}

class M_CSRs(implicit p: Parameters) extends YQModule with CSRsAddr {
  val io = IO(new YQBundle {
    val csrsW  = new CSRsW
    val csrsR  = new CSRsR
    val eip    = Input(Bool())
  })

  val MXL   = log2Down(xlen) - 4
  val MXLEN = 64

  val SPPInit  = 0
  val MPPInit  = 3
  val UXLInit  = if (extensions.contains('S')) log2Down(xlen) - 4 else 0
  val SXLInit  = if (extensions.contains('S')) log2Down(xlen) - 4 else 0
  val MPRVInit = 0
  val MXRInit  = 0
  val SUMInit  = 0
  val TVMInit  = 0
  val TWInit   = 0
  val TSRInit  = 0
  val FSInit   = 0
  val XSInit   = 0
  val SDInit   = 0

  var mstatusInit = SPPInit  << 8  | MPPInit << 11 | FSInit  << 13 | XSInit  << 15 |
                    MPRVInit << 17 | SUMInit << 18 | MXRInit << 19 | TVMInit << 20 |
                    TWInit   << 21 | TSRInit << 22 | SDInit  << (xlen - 1)
  if (xlen != 32)   mstatusInit = mstatusInit | UXLInit << 32 | SXLInit << 34

  val mipInit = 0x888.U
  val mieInit = 0x888.U

  val misa      = MXL.U(2.W) ## 0.U((MXLEN - 28).W) ## extensions.foldLeft(0)((res, x) => res | 1 << x - 'A').U(26.W)
  val mvendorid = 0.U(32.W) // non-commercial implementation
  val marchid   = 0.U(xlen.W) // the field is not implemented
  val mimpid    = 0.U(xlen.W) // the field is not implemented
  val mhartid   = 0.U(xlen.W) // the hart that running the code
  val mstatus   = MstatusInit(UInt(xlen.W), mstatusInit.U(xlen.W))
  val mtvec     = RegInit(0.U(xlen.W))

  // val medeleg // should not exist with only M-Mode
  // val mideleg // should not exist with only M-Mode

  val mcycle       = RegInit(0.U(64.W)) // the number of clock cycles
  val minstret     = RegInit(0.U(64.W)) // the number of instructions retired

  val mhpmcounters = 0.U(64.W) // a simple legal implementation
  val mhpmevents   = 0.U(xlen.W) // a simple legal implementation

  val mcycleh       = if (xlen == 32) WireDefault(0.U(32.W)) else null
  val minstreth     = if (xlen == 32) WireDefault(0.U(32.W)) else null
  val mhpmcounterhs = if (xlen == 32) Vec(29, WireDefault(0.U(32.W))) else null

  val mcounteren = RegInit(0.U(32.W)) // not needed for M-Mode only
  val scounteren = RegInit(0.U(32.W)) // not needed for M-Mode only

  val mcountinhibit = RegInit(0xFFFFFFF8L.U(32.W)) // only allow CY and IR to increase

  val mscratch = RegInit(0.U(xlen.W))
  val mepc = RegInit(0.U(xlen.W))

  val mcause = RegInit(0.U(xlen.W))
  val mtval = RegInit(0.U(xlen.W))

  val mip = MipInit(UInt(xlen.W), mipInit)
  val mie = MieInit(UInt(xlen.W), mieInit)
  val mtime = RegInit(0.U(64.W))
  val mtimecmp = RegInit(0.U(64.W))

  mcycle := mcycle + 1.U
  mtime  := mtime + 1.U

  for (i <- 0 until RegConf.writeCsrsPort) {
    when(io.csrsW.wen(i)) {
      when(io.csrsW.wcsr(i)(11, 10) === "b11".U) {
        // TODO: Raise an illegal instruction exception.
      }.otherwise {
        when(io.csrsW.wcsr(i) === Misa) {} // Currently read-only
        .elsewhen(io.csrsW.wcsr(i) === Mvendorid) {
          // TODO: Raise an illegal instruction exception.
        }.elsewhen(io.csrsW.wcsr(i) === Marchid) {
          // TODO: Raise an illegal instruction exception.
        }.elsewhen(io.csrsW.wcsr(i) === Mimpid) {
          // TODO: Raise an illegal instruction exception.
        }.elsewhen(io.csrsW.wcsr(i) === Mhartid) {
          // TODO: Raise an illegal instruction exception.
        }.elsewhen(io.csrsW.wcsr(i) === Mstatus) {
          mstatus := io.csrsW.wdata(i)

          mstatus.SPP  := 0.B
          mstatus.MPP  := "b11".U
          mstatus.FS   := 0.U
          mstatus.XS   := 0.U
          mstatus.MPRV := 0.B
          mstatus.SUM  := 0.B
          mstatus.MXR  := 0.B
          mstatus.TVM  := 0.B
          mstatus.TW   := 0.B
          mstatus.TSR  := 0.B
          mstatus.SD   := 0.B

          if (xlen != 32) {
            mstatus.UXL  := 0.B
            mstatus.SXL  := 0.B
          }
        }
        when(io.csrsW.wcsr(i) === Mtvec) {
          mtvec := io.csrsW.wdata(i)
          when(io.csrsW.wdata(i)(1, 0) >= 2.U) {
            mtvec := io.csrsW.wdata(i)(xlen - 1, 2) ## mtvec(1, 0)
          }
          // TODO: What is the legal value?
        }
        when(io.csrsW.wcsr(i) === Mip) {} // TODO: Currently do nothing.
        when(io.csrsW.wcsr(i) === Mie) { mie := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mtime) { mtime := io.csrsW.wdata(i); mip.MTIP := 0.B }
        when(io.csrsW.wcsr(i) === Mtimecmp) { mtimecmp := io.csrsW.wdata(i); mip.MTIP := 0.B }
        when(io.csrsW.wcsr(i) === Mcycle) { if (xlen != 32) mcycle := io.csrsW.wdata(i) else mcycle(31, 0) := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Minstret) { if (xlen != 32) minstret := io.csrsW.wdata(i) else minstret(31, 0) := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) >= Mhpmcounter(3.U) && io.csrsW.wcsr(i) <= Mhpmcounter(31.U)) {} // Do nothing.
        when(io.csrsW.wcsr(i) >= Mhpmevent(3.U) && io.csrsW.wcsr(i) <= Mhpmevent(31.U)) {} // Do nothing.
        when(io.csrsW.wcsr(i) === Mcountinhibit) {
          mcountinhibit := Cat(mcountinhibit(31, 3), io.csrsW.wdata(i)(2), mcountinhibit(1), io.csrsW.wdata(i)(0))
        }
        when(io.csrsW.wcsr(i) === Mscratch) { mscratch := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mepc) { mepc := io.csrsW.wdata(i)(xlen - 1, 2) ## mepc(1, 0) }
        when(io.csrsW.wcsr(i) === Mcause) {
          when(io.csrsW.wdata(i)(xlen - 1) === 1.B) {
            when((io.csrsW.wdata(i)(xlen - 2, 0) === 3.U) ||
                (io.csrsW.wdata(i)(xlen - 2, 0) === 7.U) ||
                (io.csrsW.wdata(i)(xlen - 2, 0) === 11.U)) { mcause := io.csrsW.wdata(i) }
          }.otherwise {
            when((io.csrsW.wdata(i)(xlen - 2, 0) <= 7.U) || (io.csrsW.wdata(i)(xlen - 2, 0) === 11.U)) {
              mcause := io.csrsW.wdata(i)
            }
          }
        }
        when(io.csrsW.wcsr(i) === Mtval) {} // Do nothing. A simple implementation.
        when((io.csrsW.wcsr(i) === Pmpcfg0) || (io.csrsW.wcsr(i) === Pmpcfg2)) {} // Currently do nothing.
        when((io.csrsW.wcsr(i) >= Pmpaddr(0.U)) && (io.csrsW.wcsr(i) <= Pmpaddr(15.U))) {} // Currently do nothing.

        if (xlen == 32) {
          when((io.csrsW.wcsr(i) === Pmpcfg1) || (io.csrsW.wcsr(i) === Pmpcfg3)) {} // Currently do nothing.
          .elsewhen(io.csrsW.wcsr(i) === Mcycleh) { mcycle(63, 32) := io.csrsW.wdata(i) }
          .elsewhen(io.csrsW.wcsr(i) === Minstreth) { minstret(63, 32) := io.csrsW.wdata(i) }
          .elsewhen(io.csrsW.wcsr(i) >= Mhpmcounterh(3.U) && io.csrsW.wcsr(i) <= Mhpmcounterh(31.U)) {} // Do nothing.
        }
      }
    }
  }

  for (i <- 0 until RegConf.readCsrsPort) {
    io.csrsR.rdata(i) := 0.U
    when(io.csrsR.rcsr(i) === Misa) { io.csrsR.rdata(i) := misa }
    when(io.csrsR.rcsr(i) === Mvendorid) { io.csrsR.rdata(i) := mvendorid }
    when(io.csrsR.rcsr(i) === Marchid) { io.csrsR.rdata(i) := marchid }
    when(io.csrsR.rcsr(i) === Mimpid) { io.csrsR.rdata(i) := mimpid }
    when(io.csrsR.rcsr(i) === Mhartid) { io.csrsR.rdata(i) := mhartid }
    when(io.csrsR.rcsr(i) === Mstatus) { io.csrsR.rdata(i) := mstatus }
    when(io.csrsR.rcsr(i) === Mtvec) { io.csrsR.rdata(i) := mtvec }
    when(io.csrsR.rcsr(i) === Mip) { io.csrsR.rdata(i) := Cat(mip(xlen - 1, 12), io.eip, mip(10, 8), (mtime > mtimecmp), mip(6, 0)) }
    when(io.csrsR.rcsr(i) === Mie) { io.csrsR.rdata(i) := mie }
    when(io.csrsR.rcsr(i) === Mtime) { io.csrsR.rdata(i) := mtime }
    when(io.csrsR.rcsr(i) === Mtimecmp) { io.csrsR.rdata(i) := mtimecmp }
    when(io.csrsR.rcsr(i) === Mcycle) { io.csrsR.rdata(i) := mcycle }
    when(io.csrsR.rcsr(i) === Minstret) { io.csrsR.rdata(i) := minstret }
    when(io.csrsR.rcsr(i) >= Mhpmcounter(3.U) && io.csrsR.rcsr(i) <= Mhpmcounter(31.U)) { io.csrsR.rdata(i) := 0.U }
    when(io.csrsR.rcsr(i) >= Mhpmevent(3.U) && io.csrsR.rcsr(i) <= Mhpmevent(31.U)) { io.csrsR.rdata(i) := 0.U }
    when(io.csrsR.rcsr(i) === Mcountinhibit) { io.csrsR.rdata(i) := mcountinhibit }
    when(io.csrsR.rcsr(i) === Mscratch) { io.csrsR.rdata(i) := mscratch }
    when(io.csrsR.rcsr(i) === Mepc) { io.csrsR.rdata(i) := Cat(mepc(xlen - 1, 2), 0.U(2.W)) }
    when(io.csrsR.rcsr(i) === Mcause) { io.csrsR.rdata(i) := mcause }
    when(io.csrsR.rcsr(i) === Mtval) { io.csrsR.rdata(i) := 0.U } // A simple implementation.
    when((io.csrsR.rcsr(i) === Pmpcfg0) || (io.csrsR.rcsr(i) === Pmpcfg2)) { io.csrsR.rdata(i) := 0.U }
    when((io.csrsR.rcsr(i) >= Pmpaddr(0.U)) && (io.csrsR.rcsr(i) <= Pmpaddr(15.U))) { io.csrsR.rdata(i) := 0.U }
    
    if (xlen == 32) {
      when((io.csrsR.rcsr(i) === Pmpcfg1) || (io.csrsR.rcsr(i) === Pmpcfg3)) { io.csrsR.rdata(i) := 0.U }
      .elsewhen(io.csrsR.rcsr(i) === Mcycleh) { io.csrsR.rdata(i) := mcycle(63, 32) }
      .elsewhen(io.csrsR.rcsr(i) === Minstreth) { io.csrsR.rdata(i) := minstret(63, 32) }
      .elsewhen(io.csrsR.rcsr(i) >= Mhpmcounterh(3.U) && io.csrsR.rcsr(i) <= Mhpmcounterh(31.U)) { io.csrsR.rdata(i) := 0.U }
    }
  }
}

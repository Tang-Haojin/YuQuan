package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.{GeneralConfig => p}
import p._
import chisel3.util.log2Floor

class ExceptIO extends Bundle {
  val except = Input (Bool())
  val pc     = Input (UInt(XLEN.W))
  val int    = Input (Bool())
  val excode = Input (UInt(4.W))
  val mtval  = Input (UInt(XLEN.W))
  val mtvec  = Output(UInt(XLEN.W))
  val mepc   = Output(UInt(XLEN.W))
  val mret   = Input (Bool())
}

class CSRsW extends Bundle {
  val wen   = Input(Bool())
  val wcsr  = Input(UInt(12.W))
  val wdata = Input(UInt(XLEN.W))
}
class CSRsR extends Bundle {
  val rcsr  = Input(UInt(12.W))
  val rdata = Output(UInt(XLEN.W))
}

class M_CSRs extends Module {
  val io = IO(new Bundle {
    val csrsW  = new CSRsW
    val csrsR  = new CSRsR
    val except = new ExceptIO
  })

  val MXL   = (log2Down(XLEN) - 4).U(2.W)
  val MXLEN = WireDefault(32.U)
  when (MXL === 2.U) { MXLEN := 64.U }

  val SPPInit  = 0
  val MPPInit  = 3
  val UXLInit  = if (p.Extensions.contains('S')) (log2Down(XLEN) - 4) else 0
  val SXLInit  = if (p.Extensions.contains('S')) (log2Down(XLEN) - 4) else 0
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
                    TWInit   << 21 | TSRInit << 22 | SDInit  << (XLEN - 1)
  if (XLEN != 32)   mstatusInit = mstatusInit | UXLInit << 32 | SXLInit << 34

  val mipInit = 0x888.U
  val mieInit = 0x888.U

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
  val Pmpcfg1       = if (XLEN == 32) 0x3A1.U else null
  val Pmpcfg2       = 0x3A2.U
  val Pmpcfg3       = if (XLEN == 32) 0x3A3.U else null
  val Pmpaddr       = (n: UInt) => 0x3B0.U + n

  val Mcycle        = 0xB00.U
  val Minstret      = 0xB02.U
  val Mhpmcounter   = (n: UInt) => 0xB00.U + n
  val Mcycleh       = if (XLEN == 32) 0xB80.U else null
  val Minstreth     = if (XLEN == 32) 0xB82.U else null
  val Mhpmcounterh  = if (XLEN == 32) (n: UInt) => 0xB80.U + n else null

  val Mcountinhibit = 0x320.U
  val Mhpmevent     = (n: UInt) => 0x320.U + n

  val Mtime         = 0xBFF.U // Customized
  val Mtimecmp      = 0xBFE.U // Customized


  val Extensions = p.Extensions.foldLeft(0)((res, x) => res | 1 << x - 'A').U

  val misa      = MXL << MXLEN | Extensions
  val mvendorid = 0.U(32.W) // non-commercial implementation
  val marchid   = 0.U(XLEN.W) // the field is not implemented
  val mimpid    = 0.U(XLEN.W) // the field is not implemented
  val mhartid   = 0.U(XLEN.W) // the hart that running the code
  val mstatus   = MstatusInit(UInt(XLEN.W), mstatusInit.U)
  val mtvec     = RegInit(0.U(XLEN.W))

  // val medeleg // should not exist with only M-Mode
  // val mideleg // should not exist with only M-Mode

  val mcycle       = RegInit(0.U(64.W)) // the number of clock cycles
  val minstret     = RegInit(0.U(64.W)) // the number of instructions retired

  val mhpmcounters = 0.U(64.W) // a simple legal implementation
  val mhpmevents   = 0.U(XLEN.W) // a simple legal implementation

  val mcycleh       = if (XLEN == 32) WireDefault(0.U(32.W)) else null
  val minstreth     = if (XLEN == 32) WireDefault(0.U(32.W)) else null
  val mhpmcounterhs = if (XLEN == 32) Vec(29, WireDefault(0.U(32.W))) else null

  val mcounteren = RegInit(0.U(32.W)) // not needed for M-Mode only
  val scounteren = RegInit(0.U(32.W)) // not needed for M-Mode only

  val mcountinhibit = RegInit(0xFFFFFFF8L.U(32.W)) // only allow CY and IR to increase

  val mscratch = RegInit(0.U(XLEN.W))
  val mepc = RegInit(0.U(XLEN.W))

  val mcause = RegInit(0.U(XLEN.W))
  val mtval = RegInit(0.U(XLEN.W))

  val mip = MipInit(UInt(XLEN.W), mipInit)
  val mie = MieInit(UInt(XLEN.W), mieInit)
  val mtime = RegInit(0.U(64.W))
  val mtimecmp = RegInit(0.U(64.W))

  when(io.csrsW.wen) {
    when(io.csrsW.wcsr(11, 10) === "b11".U) {
      // TODO: Raise an illegal instruction exception.
    }.otherwise {
      when(io.csrsW.wcsr === Misa) {} // Currently read-only
      .elsewhen(io.csrsW.wcsr === Mvendorid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csrsW.wcsr === Marchid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csrsW.wcsr === Mimpid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csrsW.wcsr === Mhartid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csrsW.wcsr === Mstatus) {
        val wdata = MstatusInit(io.csrsW.wdata)
        mstatus := wdata

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

        mstatus.WPRI_2  := 0.B
        mstatus.WPRI_6  := 0.B
        mstatus.WPRI_9  := 0.U
        mstatus.WPRI_23 := 0.U
          
        if (XLEN != 32) {
          mstatus.UXL  := 0.B
          mstatus.SXL  := 0.B

          mstatus.WPRI_36 := 0.U
        }
      }.elsewhen(io.csrsW.wcsr === Mtvec) {
        mtvec := io.csrsW.wdata
        when(io.csrsW.wdata(1, 0) >= 2.U) {
          mtvec := Cat(io.csrsW.wdata(XLEN - 1, 2), mtvec(1, 0))
        }
        // TODO: What is the legal value?
      }.elsewhen(io.csrsW.wcsr === Mip) {} // Currently do nothing.
      .elsewhen(io.csrsW.wcsr === Mie) {
        val wdata = MieInit(io.csrsW.wdata)
        mie.MEIE := wdata.MEIE
        mie.MSIE := wdata.MSIE
        mie.MTIE := wdata.MTIE
      }.elsewhen(io.csrsW.wcsr === Mtime) { mtime := io.csrsW.wdata; mip.MTIP := 0.B }
      .elsewhen(io.csrsW.wcsr === Mtimecmp) { mtimecmp := io.csrsW.wdata; mip.MTIP := 0.B }
      .elsewhen(io.csrsW.wcsr === Mcycle) { if (XLEN != 32) mcycle := io.csrsW.wdata else mcycle(31, 0) := io.csrsW.wdata }
      .elsewhen(io.csrsW.wcsr === Minstret) { if (XLEN != 32) minstret := io.csrsW.wdata else minstret(31, 0) := io.csrsW.wdata }
      .elsewhen(io.csrsW.wcsr >= Mhpmcounter(3.U) && io.csrsW.wcsr <= Mhpmcounter(31.U)) {} // Do nothing.
      .elsewhen(io.csrsW.wcsr >= Mhpmevent(3.U) && io.csrsW.wcsr <= Mhpmevent(31.U)) {} // Do nothing.
      .elsewhen(io.csrsW.wcsr === Mcountinhibit) {
        mcountinhibit := Cat(mcountinhibit(31, 3), io.csrsW.wdata(2), mcountinhibit(1), io.csrsW.wdata(0))
      }.elsewhen(io.csrsW.wcsr === Mscratch) { mscratch := io.csrsW.wdata }
      .elsewhen(io.csrsW.wcsr === Mepc) { mepc := Cat(io.csrsW.wdata(XLEN - 1, 2), mepc(1, 0)) }
      .elsewhen(io.csrsW.wcsr === Mcause) {
        when(io.csrsW.wdata(XLEN - 1) === 1.B) {
          when((io.csrsW.wdata(XLEN - 2, 0) === 3.U) ||
               (io.csrsW.wdata(XLEN - 2, 0) === 7.U) ||
               (io.csrsW.wdata(XLEN - 2, 0) === 11.U)) { mcause := io.csrsW.wdata }
        }.otherwise {
          when((io.csrsW.wdata(XLEN - 2, 0) <= 7.U) || (io.csrsW.wdata(XLEN - 2, 0) === 11.U)) {
            mcause := io.csrsW.wdata
          }
        }
      }
      .elsewhen(io.csrsW.wcsr === Mtval) {} // Do nothing. A simple implementation.
      .elsewhen((io.csrsW.wcsr === Pmpcfg0) || (io.csrsW.wcsr === Pmpcfg2)) {} // Currently do nothing.
      .elsewhen((io.csrsW.wcsr >= Pmpaddr(0.U)) && (io.csrsW.wcsr <= Pmpaddr(15.U))) {} // Currently do nothing.

      if (XLEN == 32) {
        when((io.csrsW.wcsr === Pmpcfg1) || (io.csrsW.wcsr === Pmpcfg3)) {} // Currently do nothing.
        .elsewhen(io.csrsW.wcsr === Mcycleh) { mcycle(63, 32) := io.csrsW.wdata }
        .elsewhen(io.csrsW.wcsr === Minstreth) { minstret(63, 32) := io.csrsW.wdata }
        .elsewhen(io.csrsW.wcsr >= Mhpmcounterh(3.U) && io.csrsW.wcsr <= Mhpmcounterh(31.U)) {} // Do nothing.
      }
    }
  }

  io.csrsR.rdata := 0.U
  when(io.csrsR.rcsr === Misa) { io.csrsR.rdata := misa }
  .elsewhen(io.csrsR.rcsr === Mvendorid) { io.csrsR.rdata := mvendorid }
  .elsewhen(io.csrsR.rcsr === Marchid) { io.csrsR.rdata := marchid }
  .elsewhen(io.csrsR.rcsr === Mimpid) { io.csrsR.rdata := mimpid }
  .elsewhen(io.csrsR.rcsr === Mhartid) { io.csrsR.rdata := mhartid }
  .elsewhen(io.csrsR.rcsr === Mstatus) { io.csrsR.rdata := mstatus }
  .elsewhen(io.csrsR.rcsr === Mtvec) { io.csrsR.rdata := mtvec }
  .elsewhen(io.csrsR.rcsr === Mip) { io.csrsR.rdata := mip }
  .elsewhen(io.csrsR.rcsr === Mie) { io.csrsR.rdata := mie }
  .elsewhen(io.csrsR.rcsr === Mtime) { io.csrsR.rdata := mtime }
  .elsewhen(io.csrsR.rcsr === Mtimecmp) { io.csrsR.rdata := mtimecmp }
  .elsewhen(io.csrsR.rcsr === Mcycle) { io.csrsR.rdata := mcycle }
  .elsewhen(io.csrsR.rcsr === Minstret) { io.csrsR.rdata := minstret }
  .elsewhen(io.csrsR.rcsr >= Mhpmcounter(3.U) && io.csrsR.rcsr <= Mhpmcounter(31.U)) { io.csrsR.rdata := 0.U }
  .elsewhen(io.csrsR.rcsr >= Mhpmevent(3.U) && io.csrsR.rcsr <= Mhpmevent(31.U)) { io.csrsR.rdata := 0.U }
  .elsewhen(io.csrsR.rcsr === Mcountinhibit) { io.csrsR.rdata := mcountinhibit }
  .elsewhen(io.csrsR.rcsr === Mscratch) { io.csrsR.rdata := mscratch }
  .elsewhen(io.csrsR.rcsr === Mepc) { io.csrsR.rdata := Cat(mepc(XLEN - 1, 2), 0.U, 0.U) }
  .elsewhen(io.csrsR.rcsr === Mcause) { io.csrsR.rdata := mcause }
  .elsewhen(io.csrsR.rcsr === Mtval) { io.csrsR.rdata := 0.U } // A simple implementation.
  .elsewhen((io.csrsR.rcsr === Pmpcfg0) || (io.csrsR.rcsr === Pmpcfg2)) { io.csrsR.rdata := 0.U }
  .elsewhen((io.csrsR.rcsr >= Pmpaddr(0.U)) && (io.csrsR.rcsr <= Pmpaddr(15.U))) { io.csrsR.rdata := 0.U }
  
  if (XLEN == 32) {
    when((io.csrsR.rcsr === Pmpcfg1) || (io.csrsR.rcsr === Pmpcfg3)) { io.csrsR.rdata := 0.U }
    .elsewhen(io.csrsR.rcsr === Mcycleh) { io.csrsR.rdata := mcycle(63, 32) }
    .elsewhen(io.csrsR.rcsr === Minstreth) { io.csrsR.rdata := minstret(63, 32) }
    .elsewhen(io.csrsR.rcsr >= Mhpmcounterh(3.U) && io.csrsR.rcsr <= Mhpmcounterh(31.U)) { io.csrsR.rdata := 0.U }
  }

  when(io.except.except) {
    when(io.except.int) {
      // TODO
    }.otherwise {
      mepc := io.except.pc
    }
    mcause  := Cat(io.except.int, Fill(XLEN - 5, 0.U), io.except.excode)
    mtval   := io.except.mtval
    mstatus := Cat(
      mstatus(XLEN - 1, 13),
      "b11".U,
      mstatus(10, 8),
      mstatus.MIE,
      mstatus(6, 4),
      0.B,
      mstatus(2, 0)
    )
  }
  when(io.except.mret) {
    mstatus := Cat(
      mstatus(XLEN - 1, 8),
      1.B,
      mstatus(6, 4),
      mstatus.MPIE,
      mstatus(2, 0)
    )
  }
  io.except.mtvec := mtvec
  io.except.mepc  := mepc
}

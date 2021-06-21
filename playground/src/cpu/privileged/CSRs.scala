package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.{GeneralConfig => p}
import p._
import chisel3.util.log2Floor

object FieldSpec {
  val fieldSpec = Enum(3)
  val wpri::wlrl::warl::Nil = fieldSpec
}

class M_CSRs extends Module {
  val io = IO(new Bundle {
    val csr   = Input(UInt(12.W))
    val wen   = Input(Bool())
    val wdata = Input(UInt(XLEN.W))
    val rdata = Output(UInt(XLEN.W))
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

  val mipInit = "b888".U(XLEN.W)
  val mieInit = "b888".U(XLEN.W)

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
  val mstatus   = MstatusInit(RegInit(mstatusInit.U(XLEN.W)))
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

  val mcountinhibit = RegInit(0xFFFFFFF8.U(32.W)) // only allow CY and IR to increase

  val mscratch = RegInit(0.U(XLEN.W))
  val mepc = RegInit(0.U(XLEN.W))

  val mcause = RegInit(0.U(XLEN.W))
  val mtval = RegInit(0.U(XLEN.W))

  val mip = MipInit(RegInit(mipInit))
  val mie = MieInit(RegInit(mieInit))
  val mtime = RegInit(0.U(64.W))
  val mtimecmp = RegInit(0.U(64.W))

  when(io.wen) {
    when(io.csr(11, 10) === "b11".U) {
      // TODO: Raise an illegal instruction exception.
    }.otherwise {
      when(io.csr === Misa) {} // Currently read-only
      .elsewhen(io.csr === Mvendorid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csr === Marchid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csr === Mimpid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csr === Mhartid) {
        // TODO: Raise an illegal instruction exception.
      }.elsewhen(io.csr === Mstatus) {
        val wdata = MstatusInit(io.wdata)
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
      }.elsewhen(io.csr === Mtvec) {
        mtvec := io.rdata
        when(io.rdata(1, 0) >= 2.U) {
          mtvec(1, 0) := mtvec(1, 0)
        }
        // TODO: What is the legal value?
      }.elsewhen(io.csr === Mip) {} // Currently do nothing.
      .elsewhen(io.csr === Mie) {
        val wdata = MieInit(io.wdata)
        mie.MEIE := wdata.MEIE
        mie.MSIE := wdata.MSIE
        mie.MTIE := wdata.MTIE
      }.elsewhen(io.csr === Mtime) { mtime := io.wdata; mip.MTIP := 0.B }
      .elsewhen(io.csr === Mtimecmp) { mtimecmp := io.wdata; mip.MTIP := 0.B }
      .elsewhen(io.csr === Mcycle) { if (XLEN != 32) mcycle := io.wdata else mcycle(31, 0) := io.wdata }
      .elsewhen(io.csr === Minstret) { if (XLEN != 32) minstret := io.wdata else minstret(31, 0) := io.wdata }
      .elsewhen(io.csr >= Mhpmcounter(3.U) && io.csr <= Mhpmcounter(31.U)) {} // Do nothing.
      .elsewhen(io.csr >= Mhpmevent(3.U) && io.csr <= Mhpmevent(31.U)) {} // Do nothing.
      .elsewhen(io.csr === Mcountinhibit) {
        mcountinhibit(0) := io.wdata(0)
        mcountinhibit(2) := io.wdata(2)
      }.elsewhen(io.csr === Mscratch) { mscratch := io.wdata }
      .elsewhen(io.csr === Mepc) { mepc(XLEN - 1, 2) := io.wdata(XLEN - 1, 2) }
      .elsewhen(io.csr === Mcause) {
        when(io.wdata(XLEN - 1) === 1.B) {
          when((io.wdata(XLEN - 2, 0) === 3.U) ||
               (io.wdata(XLEN - 2, 0) === 7.U) ||
               (io.wdata(XLEN - 2, 0) === 11.U)) { mcause := io.wdata }
        }.otherwise {
          when((io.wdata(XLEN - 2, 0) <= 7.U) || (io.wdata(XLEN - 2, 0) === 11.U)) {
            mcause := io.wdata
          }
        }
      }
      .elsewhen(io.csr === Mtval) {} // Do nothing. A simple implementation.
      .elsewhen((io.csr === Pmpcfg0) || (io.csr === Pmpcfg2)) {} // Currently do nothing.
      .elsewhen((io.csr >= Pmpaddr(0.U)) && (io.csr <= Pmpaddr(15.U))) {} // Currently do nothing.

      if (XLEN == 32) {
        when((io.csr === Pmpcfg1) || (io.csr === Pmpcfg3)) {} // Currently do nothing.
        .elsewhen(io.csr === Mcycleh) { mcycle(63, 32) := io.wdata }
        .elsewhen(io.csr === Minstreth) { minstret(63, 32) := io.wdata }
        .elsewhen(io.csr >= Mhpmcounterh(3.U) && io.csr <= Mhpmcounterh(31.U)) {} // Do nothing.
      }
    }
  }

  when(io.csr === Misa) { io.rdata := misa }
  .elsewhen(io.csr === Mvendorid) { io.rdata := mvendorid }
  .elsewhen(io.csr === Marchid) { io.rdata := marchid }
  .elsewhen(io.csr === Mimpid) { io.rdata := mimpid }
  .elsewhen(io.csr === Mhartid) { io.rdata := mhartid }
  .elsewhen(io.csr === Mstatus) { io.rdata := mstatus }
  .elsewhen(io.csr === Mtvec) { io.rdata := mtvec }
  .elsewhen(io.csr === Mip) { io.rdata := mip }
  .elsewhen(io.csr === Mie) { io.rdata := mie }
  .elsewhen(io.csr === Mtime) { io.rdata := mtime }
  .elsewhen(io.csr === Mtimecmp) { io.rdata := mtimecmp }
  .elsewhen(io.csr === Mcycle) { io.rdata := mcycle }
  .elsewhen(io.csr === Minstret) { io.rdata := minstret }
  .elsewhen(io.csr >= Mhpmcounter(3.U) && io.csr <= Mhpmcounter(31.U)) { io.rdata := 0.U }
  .elsewhen(io.csr >= Mhpmevent(3.U) && io.csr <= Mhpmevent(31.U)) { io.rdata := 0.U }
  .elsewhen(io.csr === Mcountinhibit) { io.rdata := mcountinhibit }
  .elsewhen(io.csr === Mscratch) { io.rdata := mscratch }
  .elsewhen(io.csr === Mepc) { io.rdata := Cat(mepc(XLEN - 1, 2), 0.U, 0.U) }
  .elsewhen(io.csr === Mcause) { io.rdata := mcause }
  .elsewhen(io.csr === Mtval) { io.rdata := 0.U } // A simple implementation.
  .elsewhen((io.csr === Pmpcfg0) || (io.csr === Pmpcfg2)) { io.rdata := 0.U }
  .elsewhen((io.csr >= Pmpaddr(0.U)) && (io.csr <= Pmpaddr(15.U))) { io.rdata := 0.U }
  
  if (XLEN == 32) {
    when((io.csr === Pmpcfg1) || (io.csr === Pmpcfg3)) { io.rdata := 0.U }
    .elsewhen(io.csr === Mcycleh) { io.rdata := mcycle(63, 32) }
    .elsewhen(io.csr === Minstreth) { io.rdata := minstret(63, 32) }
    .elsewhen(io.csr >= Mhpmcounterh(3.U) && io.csr <= Mhpmcounterh(31.U)) { io.rdata := 0.U }
  }
}

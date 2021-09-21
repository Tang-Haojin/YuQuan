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
  val Medeleg       = 0x302.U
  val Mideleg       = 0x303.U
  val Mie           = 0x304.U
  val Mtvec         = 0x305.U
  val Mcounteren    = 0x306.U

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

  val Cycle         = 0xC00.U
  val Time          = 0xC01.U
  val Instret       = 0xC02.U

  val Cycleh        = if (xlen == 32) 0xC80.U else null
  val Timeh         = if (xlen == 32) 0xC81.U else null
  val Instreth      = if (xlen == 32) 0xC82.U else null

  val Sstatus       = 0x100.U
  val Sie           = 0x104.U
  val Stvec         = 0x105.U
  val Scounteren    = 0x106.U
  val Sscratch      = 0x140.U
  val Sepc          = 0x141.U
  val Scause        = 0x142.U
  val Stval         = 0x143.U
  val Sip           = 0x144.U
  val Satp          = 0x180.U

  val Ustatus       = 0x000.U
  val Uie           = 0x004.U
  val Utvec         = 0x005.U
  val Uscratch      = 0x040.U
  val Uepc          = 0x041.U
  val Ucause        = 0x042.U
  val Utval         = 0x043.U
  val Uip           = 0x044.U
}

class M_CSRs(implicit p: Parameters) extends YQModule with CSRsAddr {
  val io = IO(new YQBundle {
    val csrsW       = new CSRsW
    val csrsR       = new CSRsR
    val eip         = Input (Bool())
    val retire      = Input (Bool())
    val currentPriv = Output(UInt(2.W))
    val bareSEIP    = Output(Bool())
    val bareUEIP    = Output(Bool())
    val changePriv  = Input (Bool())
    val newPriv     = Input (UInt(2.W))
    val satp        = Output(UInt(xlen.W))
    val debug       = if (Debug) new Bundle {
      val priv     = Output(UInt(2.W))
      val mstatus  = Output(UInt(xlen.W))
      val mepc     = Output(UInt(xlen.W))
      val sepc     = Output(UInt(xlen.W))
      val mtvec    = Output(UInt(xlen.W))
      val stvec    = Output(UInt(xlen.W))
      val mcause   = Output(UInt(xlen.W))
      val scause   = Output(UInt(xlen.W))
      val mie      = Output(UInt(xlen.W))
      val mscratch = Output(UInt(xlen.W))
    } else null
  })

  private val misa      = (log2Down(xlen) - 4).U(2.W) ## 0.U((xlen - 28).W) ## extensions.foldLeft(0)((res, x) => res | 1 << x - 'A').U(26.W)
  private val mvendorid = 0.U(32.W) // non-commercial implementation
  private val marchid   = 0.U(xlen.W) // the field is not implemented
  private val mimpid    = 0.U(xlen.W) // the field is not implemented
  private val mhartid   = 0.U(xlen.W) // the hart that running the code
  private val mtvec     = RegInit(0.U(xlen.W))
  private val mstatus   = RegInit({ val init = WireDefault(0.U.asTypeOf(new MstatusBundle))
    init.UXL := (if (extensions.contains('S')) log2Down(xlen) - 4 else 0).U
    init.SXL := (if (extensions.contains('S')) log2Down(xlen) - 4 else 0).U
    init
  })

  private val medeleg = if (extensions.contains('S')) RegInit(0.U(xlen.W)) else null
  private val mideleg = if (extensions.contains('S')) RegInit(0.U.asTypeOf(new MidelegBundle)) else null

  private val mcycle       = RegInit(0.U(64.W)) // the number of clock cycles
  private val minstret     = RegInit(0.U(64.W)) // the number of instructions retired

  private val mhpmcounters = 0.U(64.W) // a simple legal implementation
  private val mhpmevents   = 0.U(xlen.W) // a simple legal implementation

  private val mcycleh       = if (xlen == 32) WireDefault(0.U(32.W)) else null
  private val minstreth     = if (xlen == 32) WireDefault(0.U(32.W)) else null
  private val mhpmcounterhs = if (xlen == 32) Vec(29, WireDefault(0.U(32.W))) else null
  private val mcounteren    = 0.U(32.W) // a simple legal implementation
  private val mcountinhibit = 0.U(32.W) // a simple legal implementation

  private val mscratch = RegInit(0.U(xlen.W))
  private val mepc = RegInit(0.U(xlen.W))

  private val mcause = RegInit(0.U(5.W))
  private val mtval = RegInit(0.U(xlen.W))

  private val mip = RegInit(new MipBundle, 0.U.asTypeOf(new MipBundle))
  private val mie = RegInit(new MieBundle, 0.U.asTypeOf(new MieBundle))
  private val mtime = RegInit(0.U(64.W))
  private val mtimecmp = RegInit(0.U(64.W))

  private val sstatus    = new Sstatus(mstatus)
  private val sie        = new Sie(mie)
  private val stvec      = RegInit(0.U(xlen.W))
  private val scounteren = 0.U(32.W) // a simple legal implementation
  private val sscratch   = RegInit(0.U(xlen.W))
  private val sepc       = RegInit(0.U(xlen.W))
  private val scause     = RegInit(0.U(5.W))
  private val stval      = RegInit(0.U(xlen.W))
  private val sip        = new Sip(mip)
  private val satp       = UseSatp(RegInit(new SatpBundle, 0.U.asTypeOf(new SatpBundle)))

  private val currentPriv = RegEnable(io.newPriv, 3.U(2.W), io.changePriv)
  io.currentPriv := currentPriv

  mcycle := mcycle + 1.U
  mtime  := mtime + 1.U
  when(io.retire) { minstret := minstret + 1.U }

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
        }.elsewhen(io.csrsW.wcsr(i) === Mstatus) { mstatus := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mtvec) { mtvec := io.csrsW.wdata(i)(xlen - 1, 2) ## Mux(io.csrsW.wdata(i)(1), mtvec(1, 0), io.csrsW.wdata(i)(1, 0)) }
        when(io.csrsW.wcsr(i) === Mip) { mip := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mie) { mie := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mtime) { mtime := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mtimecmp) { mtimecmp := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mcycle) { if (xlen != 32) mcycle := io.csrsW.wdata(i) else mcycle(31, 0) := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Minstret) { if (xlen != 32) minstret := io.csrsW.wdata(i) else minstret(31, 0) := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) >= Mhpmcounter(3.U) && io.csrsW.wcsr(i) <= Mhpmcounter(31.U)) {} // Do nothing.
        when(io.csrsW.wcsr(i) >= Mhpmevent(3.U) && io.csrsW.wcsr(i) <= Mhpmevent(31.U)) {} // Do nothing.
        when(io.csrsW.wcsr(i) === Mcounteren) {} // do nothing
        when(io.csrsW.wcsr(i) === Mcountinhibit) {} // do nothing
        when(io.csrsW.wcsr(i) === Mscratch) { mscratch := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Mepc) { mepc := io.csrsW.wdata(i)(xlen - 1, 2) ## 0.U(2.W) }
        when(io.csrsW.wcsr(i) === Mcause) { mcause := io.csrsW.wdata(i)(xlen - 1) ## io.csrsW.wdata(i)(3, 0) }
        when(io.csrsW.wcsr(i) === Mtval) {} // Do nothing. A simple implementation.
        when((io.csrsW.wcsr(i) === Pmpcfg0) || (io.csrsW.wcsr(i) === Pmpcfg2)) {} // Currently do nothing.
        when((io.csrsW.wcsr(i) >= Pmpaddr(0.U)) && (io.csrsW.wcsr(i) <= Pmpaddr(15.U))) {} // Currently do nothing.
        when(io.csrsW.wcsr(i) === Sstatus) { sstatus := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Sie) { sie := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Stvec) { stvec := io.csrsW.wdata(i)(xlen - 1, 2) ## Mux(io.csrsW.wdata(i)(1, 0) >= 2.U, stvec(1, 0), io.csrsW.wdata(i)(1, 0)) }
        when(io.csrsW.wcsr(i) === Scounteren) {} // do nothing
        when(io.csrsW.wcsr(i) === Sscratch) { sscratch := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Sepc) { sepc := io.csrsW.wdata(i)(xlen - 1, 2) ## 0.U(2.W) }
        when(io.csrsW.wcsr(i) === Scause) { scause := io.csrsW.wdata(i)(xlen - 1) ## io.csrsW.wdata(i)(3, 0) }
        when(io.csrsW.wcsr(i) === Stval) { stval := io.csrsW.wdata(i) } // TODO: set the value when exception is arised
        when(io.csrsW.wcsr(i) === Sip) { sip := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Satp) { satp := io.csrsW.wdata(i) } // TODO: support paging
        when(io.csrsW.wcsr(i) === Mideleg) { if (extensions.contains('S')) mideleg := io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) === Medeleg) { if (extensions.contains('S')) medeleg := io.csrsW.wdata(i) }

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
    when(io.csrsR.rcsr(i) === Mstatus) { io.csrsR.rdata(i) := mstatus.asUInt }
    when(io.csrsR.rcsr(i) === Mtvec) { io.csrsR.rdata(i) := mtvec }
    when(io.csrsR.rcsr(i) === Mip) { io.csrsR.rdata(i) := { val data = WireDefault(new MipBundle, mip)
      data.WPRI_0 := 0.U; data.WPRI_1 := 0.B; data.WPRI_2 := 0.B; data.WPRI_3 := 0.B
      data.MEIP := io.eip; data.MTIP := (mtime > mtimecmp); data.SEIP := mip.SEIP || io.eip
      data.asUInt
    }}
    when(io.csrsR.rcsr(i) === Mie) { io.csrsR.rdata(i) := mie.asUInt }
    when(io.csrsR.rcsr(i) === Mtime || io.csrsR.rcsr(i) === Time) { io.csrsR.rdata(i) := mtime }
    when(io.csrsR.rcsr(i) === Mtimecmp) { io.csrsR.rdata(i) := mtimecmp }
    when(io.csrsR.rcsr(i) === Mcycle || io.csrsR.rcsr(i) === Cycle) { io.csrsR.rdata(i) := mcycle }
    when(io.csrsR.rcsr(i) === Minstret || io.csrsR.rcsr(i) === Instret) { io.csrsR.rdata(i) := minstret }
    when(io.csrsR.rcsr(i) >= Mhpmcounter(3.U) && io.csrsR.rcsr(i) <= Mhpmcounter(31.U)) { io.csrsR.rdata(i) := 0.U }
    when(io.csrsR.rcsr(i) >= Mhpmevent(3.U) && io.csrsR.rcsr(i) <= Mhpmevent(31.U)) { io.csrsR.rdata(i) := 0.U }
    when(io.csrsR.rcsr(i) === Mcounteren) { io.csrsR.rdata(i) := mcounteren }
    when(io.csrsR.rcsr(i) === Mcountinhibit) { io.csrsR.rdata(i) := mcountinhibit }
    when(io.csrsR.rcsr(i) === Mscratch) { io.csrsR.rdata(i) := mscratch }
    when(io.csrsR.rcsr(i) === Mepc) { io.csrsR.rdata(i) := mepc(xlen - 1, 2) ## 0.U(2.W) }
    when(io.csrsR.rcsr(i) === Mcause) { io.csrsR.rdata(i) := mcause(4) ## 0.U((xlen - 5).W) ## mcause(3, 0) }
    when(io.csrsR.rcsr(i) === Mtval) { io.csrsR.rdata(i) := 0.U } // A simple implementation.
    when((io.csrsR.rcsr(i) === Pmpcfg0) || (io.csrsR.rcsr(i) === Pmpcfg2)) { io.csrsR.rdata(i) := 0.U }
    when((io.csrsR.rcsr(i) >= Pmpaddr(0.U)) && (io.csrsR.rcsr(i) <= Pmpaddr(15.U))) { io.csrsR.rdata(i) := 0.U }
    when(io.csrsR.rcsr(i) === Sstatus) { io.csrsR.rdata(i) := sstatus}
    when(io.csrsR.rcsr(i) === Sie) { io.csrsR.rdata(i) := sie }
    when(io.csrsR.rcsr(i) === Stvec) { io.csrsR.rdata(i) := stvec }
    when(io.csrsR.rcsr(i) === Scounteren) { io.csrsR.rdata(i) := scounteren }
    when(io.csrsR.rcsr(i) === Sscratch) { io.csrsR.rdata(i) := sscratch }
    when(io.csrsR.rcsr(i) === Sepc) { io.csrsR.rdata(i) := sepc(xlen - 1, 2) ## 0.U(2.W) }
    when(io.csrsR.rcsr(i) === Scause) { io.csrsR.rdata(i) := scause(4) ## 0.U((xlen - 5).W) ## scause(3, 0) }
    when(io.csrsR.rcsr(i) === Stval) { io.csrsR.rdata(i) := stval }
    when(io.csrsR.rcsr(i) === Sip) { io.csrsR.rdata(i) := sip }
    when(io.csrsR.rcsr(i) === Satp) { io.csrsR.rdata(i) := satp.asUInt }
    when(io.csrsR.rcsr(i) === Mideleg) { if (extensions.contains('S')) io.csrsR.rdata(i) := mideleg.asUInt }
    when(io.csrsR.rcsr(i) === Medeleg) { if (extensions.contains('S')) io.csrsR.rdata(i) := medeleg }

    if (xlen == 32) {
      when((io.csrsR.rcsr(i) === Pmpcfg1) || (io.csrsR.rcsr(i) === Pmpcfg3)) { io.csrsR.rdata(i) := 0.U }
      when(io.csrsR.rcsr(i) === Mcycleh || io.csrsR.rcsr(i) === Cycleh) { io.csrsR.rdata(i) := mcycle(63, 32) }
      when(io.csrsR.rcsr(i) === Minstreth || io.csrsR.rcsr(i) === Instreth) { io.csrsR.rdata(i) := minstret(63, 32) }
      when(io.csrsR.rcsr(i) >= Mhpmcounterh(3.U) && io.csrsR.rcsr(i) <= Mhpmcounterh(31.U)) { io.csrsR.rdata(i) := 0.U }
      when(io.csrsR.rcsr(i) === Timeh) { io.csrsR.rdata(i) := mtime(63, 32) }
    }
  }

  io.bareSEIP := mip.SEIP; io.bareUEIP := mip.UEIP
  io.satp := satp

  if (Debug) {
    io.debug.priv     := currentPriv
    io.debug.mstatus  := mstatus.asUInt
    io.debug.mepc     := mepc
    io.debug.sepc     := sepc
    io.debug.mtvec    := mtvec
    io.debug.stvec    := stvec
    io.debug.mcause   := mcause
    io.debug.scause   := scause
    io.debug.mie      := mie.asUInt
    io.debug.mscratch := mscratch
  }
}

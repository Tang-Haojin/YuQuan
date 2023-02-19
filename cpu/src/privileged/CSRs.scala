package cpu.privileged

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.CPUParams
import cpu.tools._

class CSRsW(implicit p: Parameters) extends YQBundle {
  val wen   = Input(Vec(RegConf.writeCsrsPort, Bool()))
  val wcsr  = Input(Vec(RegConf.writeCsrsPort, UInt(12.W)))
  val wdata = Input(Vec(RegConf.writeCsrsPort, UInt(xlen.W)))
}
class CSRsR(implicit p: Parameters) extends YQBundle {
  val rcsr  = Input (UInt(12.W))
  val rdata = Output(UInt(xlen.W))
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
  val Fflags        = 0x001.U
  val Frm           = 0x002.U
  val Fcsr          = 0x003.U
  val Uie           = 0x004.U
  val Utvec         = 0x005.U
  val Uscratch      = 0x040.U
  val Uepc          = 0x041.U
  val Ucause        = 0x042.U
  val Utval         = 0x043.U
  val Uip           = 0x044.U
}

abstract class AbstractCSRs(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val csrsW       = new CSRsW
    val csrsR       = Vec(RegConf.readCsrsPort, new CSRsR)
    val mmuRead     = Vec(10, new CSRsR)
    val meip        = Input (Bool())
    val seip        = Input (Bool())
    val retire      = Input (Bool())
    val changePriv  = Input (Bool())
    val newPriv     = Input (UInt(2.W))
    val mtime       = Input (UInt(64.W))
    val mtip        = Input (Bool())
    val msip        = Input (Bool())
    val currentPriv = Output(UInt(2.W))
    val bareSEIP    = Output(Bool())
    val bareUEIP    = Output(Bool())
    val interrupt   = Input (if (isLxb) UInt(8.W) else Bool())
    val debug       = if (Debug) new Bundle {
      val priv     = Output(UInt(2.W))
      val mstatus  = Output(UInt(xlen.W))
      val mepc     = Output(UInt(xlen.W))
      val sepc     = Output(UInt(xlen.W))
      val mtvec    = Output(UInt(xlen.W))
      val stvec    = Output(UInt(xlen.W))
      val mcause   = Output(UInt(xlen.W))
      val scause   = Output(UInt(xlen.W))
      val mtval    = Output(UInt(xlen.W))
      val stval    = Output(UInt(xlen.W))
      val mie      = Output(UInt(xlen.W))
      val mscratch = Output(UInt(xlen.W))
    } else null
  })
}

class CSRs(implicit p: Parameters) extends AbstractCSRs with CSRsAddr {
  private val misa      = (log2Down(xlen) - 4).U(2.W) ## 0.U((xlen - 28).W) ## extensions.foldLeft(0)((res, x) => res | 1 << x - 'A').U(26.W)
  private val mvendorid = 0.U(32.W) // non-commercial implementation
  private val marchid   = 0.U(xlen.W) // the field is not implemented
  private val mimpid    = 0.U(xlen.W) // the field is not implemented
  private val mhartid   = 0.U(xlen.W) // the hart that running the code
  private val mtvec     = RegInit(0.U(xlen.W))
  private val mstatus   = RegInit({ val init = WireDefault(0.U.asTypeOf(new MstatusBundle))
    init.UXL := (if (ext('S')) log2Down(xlen) - 4 else 0).U
    init.SXL := (if (ext('S')) log2Down(xlen) - 4 else 0).U
    init
  })

  private val medeleg = if (ext('S')) RegInit(0.U(xlen.W)) else null
  private val mideleg = if (ext('S')) RegInit(0.U.asTypeOf(new MidelegBundle)) else null

  private val mcycle       = RegInit(0.U(64.W)) // the number of clock cycles
  private val minstret     = RegInit(0.U(64.W)) // the number of instructions retired

  private val mhpmcounters = 0.U(64.W) // a simple legal implementation
  private val mhpmevents   = 0.U(xlen.W) // a simple legal implementation

  private val mcycleh       = if (xlen == 32) WireDefault(0.U(32.W)) else null
  private val minstreth     = if (xlen == 32) WireDefault(0.U(32.W)) else null
  private val mhpmcounterhs = if (xlen == 32) VecInit(Seq.fill(29)(WireDefault(0.U(32.W)))) else null
  private val mcounteren    = 0.U(32.W) // a simple legal implementation
  private val mcountinhibit = 0.U(32.W) // a simple legal implementation

  private val mscratch = RegInit(0.U(xlen.W))
  private val mepc = RegInit(0.U(xlen.W))

  private val mcause = RegInit(0.U(5.W))
  private val mtval = RegInit(0.U(xlen.W))

  private val mip = RegInit(new MipBundle, 0.U.asTypeOf(new MipBundle))
  private val mie = RegInit(new MieBundle, 0.U.asTypeOf(new MieBundle))

  private val sstatus    = if (ext('S')) new Sstatus(mstatus) else null
  private val sie        = if (ext('S')) new Sie(mie) else null
  private val stvec      = if (ext('S')) RegInit(0.U(xlen.W)) else null
  private val scounteren = 0.U(32.W) // a simple legal implementation
  private val sscratch   = if (ext('S')) RegInit(0.U(xlen.W)) else null
  private val sepc       = if (ext('S')) RegInit(0.U(xlen.W)) else null
  private val scause     = if (ext('S')) RegInit(0.U(5.W)) else null
  private val stval      = if (ext('S')) RegInit(0.U(xlen.W)) else null
  private val sip        = if (ext('S')) new Sip(mip) else null
  private val satp       = if (ext('S')) UseSatp(RegInit(new SatpBundle, 0.U.asTypeOf(new SatpBundle))) else null

  private val currentPriv = RegEnable(io.newPriv, 3.U(2.W), io.changePriv)
  io.currentPriv := (if (ext('S') || ext('U')) currentPriv else "b11".U)

  mcycle := mcycle + 1.U
  if (!isZmb) when(io.retire) { minstret := minstret + 1.U }

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
        }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mstatus) { mstatus := io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mtvec) { mtvec := io.csrsW.wdata(i)(xlen - 1, 2) ## Mux(io.csrsW.wdata(i)(1), mtvec(1, 0), io.csrsW.wdata(i)(1, 0)) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mip) { mip := io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mie) { mie := io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mcycle) { if (xlen != 32) mcycle := io.csrsW.wdata(i) else mcycle := mcycle(63, 32) ## io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Minstret) { if (xlen != 32) minstret := io.csrsW.wdata(i) else minstret := minstret(63, 32) ## io.csrsW.wdata(i) }
        when(io.csrsW.wcsr(i) >= Mhpmcounter(3.U) && io.csrsW.wcsr(i) <= Mhpmcounter(31.U)) {} // Do nothing.
        when(io.csrsW.wcsr(i) >= Mhpmevent(3.U) && io.csrsW.wcsr(i) <= Mhpmevent(31.U)) {} // Do nothing.
        when(io.csrsW.wcsr(i) === Mcounteren) {} // do nothing
        when(io.csrsW.wcsr(i) === Mcountinhibit) {} // do nothing
        if (!isZmb) when(io.csrsW.wcsr(i) === Mscratch) { mscratch := io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mepc) { mepc := (if (ext('C')) io.csrsW.wdata(i)(xlen - 1, 1) ## 0.B else io.csrsW.wdata(i)(xlen - 1, 2) ## 0.U(2.W)) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mcause) { mcause := io.csrsW.wdata(i)(xlen - 1) ## io.csrsW.wdata(i)(3, 0) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mtval) { mtval := io.csrsW.wdata(i) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Sstatus) { sstatus := io.csrsW.wdata(i) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Sie) { sie := io.csrsW.wdata(i) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Stvec) { stvec := io.csrsW.wdata(i)(xlen - 1, 2) ## Mux(io.csrsW.wdata(i)(1, 0) >= 2.U, stvec(1, 0), io.csrsW.wdata(i)(1, 0)) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Scounteren) {} // do nothing
        if (ext('S')) when(io.csrsW.wcsr(i) === Sscratch) { sscratch := io.csrsW.wdata(i) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Sepc) { sepc := (if (ext('C')) io.csrsW.wdata(i)(xlen - 1, 1) ## 0.B else io.csrsW.wdata(i)(xlen - 1, 2) ## 0.U(2.W)) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Scause) { scause := io.csrsW.wdata(i)(xlen - 1) ## io.csrsW.wdata(i)(3, 0) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Stval) { stval := io.csrsW.wdata(i) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Sip) { sip := io.csrsW.wdata(i) }
        if (ext('S')) when(io.csrsW.wcsr(i) === Satp) { satp := io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Mideleg) { if (ext('S')) mideleg := io.csrsW.wdata(i) }
        if (!isZmb) when(io.csrsW.wcsr(i) === Medeleg) { if (ext('S')) medeleg := io.csrsW.wdata(i) }

        if (xlen == 32) {
          if (!isZmb) when(io.csrsW.wcsr(i) === Mcycleh) { mcycle := io.csrsW.wdata(i) ## mcycle(31, 0) }
          if (!isZmb) when(io.csrsW.wcsr(i) === Minstreth) { minstret := io.csrsW.wdata(i) ## minstret(31, 0) }
          when(io.csrsW.wcsr(i) >= Mhpmcounterh(3.U) && io.csrsW.wcsr(i) <= Mhpmcounterh(31.U)) {} // Do nothing.
        }
      }
    }
  }

  private val readPorts = io.csrsR ++ io.mmuRead
  readPorts.foreach(r => (r.rcsr, r.rdata) match { case (rcsr, rdata) => {
    rdata := 0.U
    if (!isZmb) when(rcsr === Misa) { rdata := misa }
    when(rcsr === Mvendorid) { rdata := mvendorid }
    when(rcsr === Marchid) { rdata := marchid }
    when(rcsr === Mimpid) { rdata := mimpid }
    when(rcsr === Mhartid) { rdata := mhartid }
    if (!isZmb) when(rcsr === Mstatus) { rdata := mstatus.asUInt }
    if (!isZmb) when(rcsr === Mtvec) { rdata := mtvec }
    if (!isZmb) when(rcsr === Mip) { rdata := { val data = WireDefault(new MipBundle, mip)
      data.WPRI_0 := 0.U; data.WPRI_1 := 0.B; data.WPRI_2 := 0.B; data.WPRI_3 := 0.B
      data.MEIP := io.meip; data.MTIP := io.mtip; data.SEIP := mip.SEIP || io.seip
      data.MSIP := io.msip; data.asUInt
    }}
    if (!isZmb) when(rcsr === Mie) { rdata := mie.asUInt }
    if (!isZmb) when(rcsr === Mcycle || rcsr === Cycle) { rdata := mcycle }
    if (!isZmb) when(rcsr === Minstret || rcsr === Instret) { rdata := minstret }
    when(rcsr >= Mhpmcounter(3.U) && rcsr <= Mhpmcounter(31.U)) { rdata := 0.U }
    when(rcsr >= Mhpmevent(3.U) && rcsr <= Mhpmevent(31.U)) { rdata := 0.U }
    when(rcsr === Mcounteren) { rdata := mcounteren }
    when(rcsr === Mcountinhibit) { rdata := mcountinhibit }
    if (!isZmb) when(rcsr === Mscratch) { rdata := mscratch }
    if (!isZmb) when(rcsr === Mepc) { rdata := (if (ext('C')) mepc(xlen - 1, 1) ## 0.B else mepc(xlen - 1, 2) ## 0.U(2.W)) }
    if (!isZmb) when(rcsr === Mcause) { rdata := mcause(4) ## 0.U((xlen - 5).W) ## mcause(3, 0) }
    if (!isZmb) when(rcsr === Mtval) { rdata := mtval }
    if (ext('S')) when(rcsr === Sstatus) { rdata := sstatus}
    if (ext('S')) when(rcsr === Sie) { rdata := sie }
    if (ext('S')) when(rcsr === Stvec) { rdata := stvec }
    if (ext('S')) when(rcsr === Scounteren) { rdata := scounteren }
    if (ext('S')) when(rcsr === Sscratch) { rdata := sscratch }
    if (ext('S')) when(rcsr === Sepc) { rdata := (if (ext('C')) sepc(xlen - 1, 1) ## 0.B else sepc(xlen - 1, 2) ## 0.U(2.W)) }
    if (ext('S')) when(rcsr === Scause) { rdata := scause(4) ## 0.U((xlen - 5).W) ## scause(3, 0) }
    if (ext('S')) when(rcsr === Stval) { rdata := stval }
    if (ext('S')) when(rcsr === Sip) { rdata := sip }
    if (ext('S')) when(rcsr === Satp) { rdata := satp }
    if (!isZmb) when(rcsr === Mideleg) { if (ext('S')) rdata := mideleg.asUInt }
    if (!isZmb) when(rcsr === Medeleg) { if (ext('S')) rdata := medeleg }
    if (useClint) when(rcsr === Time) { rdata := io.mtime }

    if (xlen == 32) {
      if (!isZmb) when(rcsr === Mcycleh || rcsr === Cycleh) { rdata := mcycle(63, 32) }
      if (!isZmb) when(rcsr === Minstreth || rcsr === Instreth) { rdata := minstret(63, 32) }
      when(rcsr >= Mhpmcounterh(3.U) && rcsr <= Mhpmcounterh(31.U)) { rdata := 0.U }
      if (useClint) when(rcsr === Timeh) { rdata := io.mtime(63, 32) }
    }
  }})

  io.bareSEIP := mip.SEIP; io.bareUEIP := mip.UEIP

  if (Debug) {
    io.debug.priv     := currentPriv
    io.debug.mstatus  := mstatus.asUInt
    io.debug.mepc     := mepc
    io.debug.mtvec    := mtvec
    io.debug.mcause   := mcause(4) ## 0.U((xlen - 5).W) ## mcause(3, 0)
    io.debug.mtval    := mtval
    io.debug.mie      := mie.asUInt
    io.debug.mscratch := mscratch
    io.debug.sepc     := (if (ext('S')) sepc else 0.U)
    io.debug.stvec    := (if (ext('S')) stvec else 0.U)
    io.debug.scause   := (if (ext('S')) scause(4) ## 0.U((xlen - 5).W) ## scause(3, 0) else 0.U)
    io.debug.stval    := (if (ext('S')) stval else 0.U)
  }
}

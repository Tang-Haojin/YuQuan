package cpu.component.mmu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import cpu.tools._
import cpu.cache._
import cpu.privileged._

class MMU(implicit p: Parameters) extends YQModule with CacheParams {
  val io = IO(new YQBundle {
    val ifIO     = new PipelineIO(32)
    val memIO    = new PipelineIO
    val icacheIO = Flipped(new CpuIO(32))
    val dcacheIO = Flipped(new CpuIO)
    val satp     = Input (UInt(xlen.W))
    val priv     = Input (UInt(2.W))
    val jmpBch   = Input (Bool())
    val sum      = Input (Bool())
    val mprv     = Input (Bool())
    val mpp      = Input (UInt(2.W))
  })

  private val idle::walking::writing::Nil = Enum(3)
  private val ifWalking::memWalking::Nil = Enum(2)
  private val stage = RegInit(0.U(2.W))
  private val level = RegInit(0.U(2.W))
  private val tlb = new TLB
  private val crossCache = RegInit(0.B)
  private val crossAddrP = RegInit(0.U((39 - Offset).W))
  private val crossAddr  = Fill(valen - 39, crossAddrP(39 - Offset - 1)) ## crossAddrP ## 0.U(Offset.W)
  private val ifVaddr  = Mux(crossCache, crossAddr, io.ifIO.pipelineReq.cpuReq.addr).asTypeOf(new Vaddr)
  private val memVaddr = io.memIO.pipelineReq.cpuReq.addr.asTypeOf(new Vaddr)
  private val vaddr    = RegInit(new Vaddr, 0.U.asTypeOf(new Vaddr))
  private val satp     = UseSatp(io.satp)
  private val pte      = RegInit(new PTE, 0.U.asTypeOf(new PTE))
  private val newPte   = io.dcacheIO.cpuResult.data.asTypeOf(new PTE)
  private val current  = RegInit(0.U(1.W))
  private val isWrite  = io.memIO.pipelineReq.cpuReq.rw
  private val ptePpn   = RegInit(0.U(44.W))
  private val isSv39_i = io.priv <= "b01".U && satp.mode === 8.U
  private val isSv39_d = Mux(io.mprv, io.mpp, io.priv) <= "b01".U && satp.mode === 8.U
  private val (ifDel  , memDel  ) = (RegInit(0.B), RegInit(0.B))
  private val (ifReady, memReady) = (RegInit(0.B), RegInit(0.B))
  private val (ifExcpt, memExcpt) = (RegInit(0.B), RegInit(0.B))
  private val (ifCause, memCause) = (RegInit(0.U(4.W)), RegInit(0.U(4.W)))
  private val ifCrossCache = RegInit(0.B)
  private val (isU_i, isS_i, isM_i) = (io.priv === "b00".U, io.priv === "b01".U, io.priv === "b11".U)
  private val (isU_d, isS_d, isM_d) = (
    Mux(io.mprv, io.mpp, io.priv) === "b00".U,
    Mux(io.mprv, io.mpp, io.priv) === "b01".U,
    Mux(io.mprv, io.mpp, io.priv) === "b11".U
  )
  private val icacheValid = WireDefault(Bool(), io.ifIO.pipelineReq.cpuReq.valid)
  private val dcacheValid = WireDefault(Bool(), io.memIO.pipelineReq.cpuReq.valid)
  private val icacheReady = WireDefault(Bool(), io.icacheIO.cpuResult.ready)
  private val partialInst = RegInit(0.U(16.W))

  when(ifDel) { ifDel := 0.B }; when(memDel) { memDel := 0.B }

  io.ifIO.pipelineResult.cause       := 0.U
  io.ifIO.pipelineResult.exception   := 0.B
  io.ifIO.pipelineResult.fromMem     := 0.B
  io.ifIO.pipelineResult.crossCache  := 0.B
  io.memIO.pipelineResult.cause      := 0.U
  io.memIO.pipelineResult.exception  := 0.B
  io.memIO.pipelineResult.fromMem    := DontCare
  io.memIO.pipelineResult.crossCache := DontCare

  io.ifIO.pipelineReq.cpuReq        <> io.icacheIO.cpuReq
  io.ifIO.pipelineResult.cpuResult  <> io.icacheIO.cpuResult
  io.memIO.pipelineReq.cpuReq       <> io.dcacheIO.cpuReq
  io.memIO.pipelineResult.cpuResult <> io.dcacheIO.cpuResult
  io.icacheIO.cpuReq.valid  := icacheValid
  io.dcacheIO.cpuReq.valid  := dcacheValid
  io.dcacheIO.cpuReq.revoke := 0.B
  io.ifIO.pipelineResult.cpuResult.ready := icacheReady

  when(ifDel) {
    icacheReady := ifReady
    io.ifIO.pipelineResult.exception := ifExcpt
    io.ifIO.pipelineResult.cause := ifCause
    io.ifIO.pipelineResult.crossCache := ifCrossCache
    io.ifIO.pipelineResult.fromMem := memExcpt
  }
  when(memDel) {
    io.memIO.pipelineResult.cpuResult.ready := memReady
    io.memIO.pipelineResult.exception := memExcpt
    io.memIO.pipelineResult.cause := memCause
  }

  when(isSv39_i) { io.icacheIO.cpuReq.addr := tlb.translate(ifVaddr) }
  when(isSv39_d) { io.dcacheIO.cpuReq.addr := tlb.translate(memVaddr) }
  when(isSv39_i && !tlb.isHit(ifVaddr)) {
    ifDel := 1.B
    ifReady := 0.B
    icacheValid := 0.B
  }
  when(isSv39_d && (!tlb.isHit(memVaddr) || (isWrite && !tlb.isDirty(memVaddr)))) {
    memDel := 1.B
    memReady := 0.B
    dcacheValid := 0.B
  }

  when(isSv39_i || isSv39_d) {
    when(stage === walking) {
      dcacheValid := 1.B
      io.dcacheIO.cpuReq.rw    := 0.B
      io.dcacheIO.cpuReq.addr  := Mux(level === 2.U, satp.ppn, pte.ppn) ## vaddr.vpn(level) ## 0.U(3.W)
      io.memIO.pipelineResult.cpuResult.ready := 0.B
      when(io.dcacheIO.cpuResult.ready) {
        pte := io.dcacheIO.cpuResult.data
        when(newPte.v) {
          dcacheValid := 0.B
          val leaf = (level === 0.B) || newPte.w || newPte.r || newPte.x
          when(leaf) { // TODO: MXR
            stage := idle
            when((!newPte.w && !newPte.r && !newPte.x) ||                       // this should be a leaf
                 (newPte.w && !newPte.r) ||                                     // that with w must have r
                 (level === 2.U && (newPte.ppn(1) ## newPte.ppn(0)) =/= 0.U) || // misaligned gigapage
                 (level === 1.U && newPte.ppn(0) =/= 0.U)) {                    // misaligned megapage
              when(current === ifWalking) { IfRaiseException(12.U) } // Instruction page fault
              .otherwise { MemRaiseException(Mux(isWrite, 15.U, 13.U)) } // load/store/amo page fault
            }.elsewhen(current === ifWalking && (isU_i && !newPte.u || isS_i && newPte.u)) { IfRaiseException(12.U) } // Instruction page fault
            .elsewhen(current === memWalking && (isU_d && !newPte.u || isS_d && newPte.u && !io.sum)) { MemRaiseException(Mux(isWrite, 15.U, 13.U)) } // load/store/amo page fault
            .elsewhen(current === ifWalking && !newPte.x) { IfRaiseException(12.U) } // Instruction page fault
            .elsewhen(current === memWalking && !isWrite && !newPte.r) { MemRaiseException(13.U) } // load page fault
            .elsewhen(current === memWalking && isWrite && !newPte.w) { MemRaiseException(15.U) } // store/amo page fault
            .elsewhen(!newPte.a || (current === memWalking && isWrite && !newPte.d)) { stage := writing; ptePpn := Mux(level === 2.U, satp.ppn, pte.ppn) }
            .otherwise { tlb.update(vaddr, newPte, level) }
          }.otherwise { level := level - 1.U }
        }.otherwise {
          when(current === ifWalking) { IfRaiseException(12.U) } // Instruction page fault
          .otherwise { MemRaiseException(Mux(isWrite, 15.U, 13.U)) } // load/store/amo page fault
        }
      }
    }

    when(stage === writing) {
      val writingPte = WireDefault(new PTE, pte)
      writingPte.a := 1.B
      writingPte.d := (current === memWalking && isWrite) | pte.d
      dcacheValid := 1.B
      io.dcacheIO.cpuReq.addr  := MuxLookup(level, ptePpn ## vaddr.vpn(0), Seq(
        1.U -> ptePpn(43, 9 ) ## vaddr.vpn(1) ## vaddr.vpn(0),
        2.U -> ptePpn(43, 18) ## vaddr.vpn.asUInt()
      )) ## 0.U(3.W)
      io.dcacheIO.cpuReq.rw    := 1.B
      io.dcacheIO.cpuReq.wmask := "b11111111".U
      io.dcacheIO.cpuReq.data  := writingPte.asUInt
      io.memIO.pipelineResult.cpuResult.ready := 0.B
      when(io.dcacheIO.cpuResult.ready) {
        tlb.update(vaddr, writingPte, level)
        dcacheValid := 0.B
        stage := idle
      }
    }
  }

  when(io.memIO.pipelineReq.cpuReq.valid && io.memIO.pipelineReq.flush) {
    tlb.flush
    memDel := !memDel; memReady := 1.B; memCause := 0.U; memExcpt := 0.B
    io.dcacheIO.cpuReq.valid := 0.B
  }.elsewhen(io.memIO.pipelineReq.cpuReq.valid && (
    (io.memIO.pipelineReq.cpuReq.size === 1.U && io.memIO.pipelineReq.cpuReq.addr(0)) ||
    (io.memIO.pipelineReq.cpuReq.size === 2.U && io.memIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) ||
    (io.memIO.pipelineReq.cpuReq.size === 3.U && io.memIO.pipelineReq.cpuReq.addr(2, 0) =/= 0.U)
  )) {
    io.dcacheIO.cpuReq.valid := 0.B
    MemRaiseException(Mux(isWrite, 6.U, 4.U), false) // load/store/amo address misaligned
  }.elsewhen(dcacheValid && !PMA(io.dcacheIO.cpuReq.addr, io.dcacheIO.cpuReq.size, isWrite)) {
    io.dcacheIO.cpuReq.valid := 0.B
    when(stage === walking && current === ifWalking) { IfRaiseException(1.U, false) } // instruction access fault
    .otherwise { MemRaiseException(Mux(stage === walking || !isWrite, 5.U, 7.U), false) } // load/store/amo access fault
    stage := idle
  }.elsewhen(io.ifIO.pipelineReq.cpuReq.valid && ifVaddr.offset(1, 0) =/= 0.U && (!ext('C')).B) {
    icacheValid := 0.B
    IfRaiseException(0.U, false) // Instruction address misaligned
  }.elsewhen(icacheValid && !PMA(io.icacheIO.cpuReq.addr, io.icacheIO.cpuReq.size, 2.U)) {
    io.icacheIO.cpuReq.valid := 0.B
    IfRaiseException(1.U, false) // instruction access fault
  }.otherwise {
    when(isSv39_i && io.ifIO.pipelineReq.cpuReq.valid) {
      when(ifVaddr.getHigher.andR =/= ifVaddr.getHigher.orR) { IfRaiseException(12.U, false); io.icacheIO.cpuReq.valid := 0.B } // Instruction page fault
      .elsewhen(tlb.isHit(ifVaddr)) {
        when(isU_i && !tlb.isUser(ifVaddr) || isS_i && tlb.isUser(ifVaddr)) { IfRaiseException(12.U, false); io.icacheIO.cpuReq.valid := 0.B } // Instruction page fault
        .elsewhen(!tlb.canExec(ifVaddr)) { IfRaiseException(12.U, false); io.icacheIO.cpuReq.valid := 0.B } // Instruction page fault
      }.elsewhen(!dcacheValid && stage === idle) {
        current := ifWalking
        stage := walking
        vaddr := ifVaddr
        level := 2.U
      }
    }

    when(isSv39_d && io.memIO.pipelineReq.cpuReq.valid && stage === idle) {
      val willWalk = WireDefault(0.B)
      when(memVaddr.getHigher.andR =/= memVaddr.getHigher.orR) { MemRaiseException(Mux(isWrite, 15.U, 13.U), false); io.dcacheIO.cpuReq.valid := 0.B } // load/store/amo page fault
      .elsewhen(tlb.isHit(memVaddr)) {
        when(isU_d && !tlb.isUser(memVaddr) || isS_d && tlb.isUser(memVaddr) && !io.sum) { MemRaiseException(Mux(isWrite, 15.U, 13.U), false); io.dcacheIO.cpuReq.valid := 0.B } // load/store/amo page fault
        .elsewhen(!isWrite && !tlb.canRead(memVaddr)) { MemRaiseException(13.U, false); io.dcacheIO.cpuReq.valid := 0.B } // load page fault
        .elsewhen(isWrite && !tlb.canWrite(memVaddr)) { MemRaiseException(15.U, false); io.dcacheIO.cpuReq.valid := 0.B } // store/amo page fault
        .elsewhen(isWrite && !tlb.isDirty(memVaddr)) { willWalk := 1.B }
      }.otherwise { willWalk := 1.B }
      when(willWalk) {
        current := memWalking
        stage := walking
        vaddr := memVaddr
        level := 2.U
      }
    }
  }

  if (ext('C')) when(!ifDel && !crossCache && icacheReady && io.ifIO.pipelineReq.offset === (BlockSize - 2).U) {
    when(io.icacheIO.cpuResult.data(1, 0) === "b11".U) {
      crossCache := 1.B
      io.ifIO.pipelineResult.cpuResult.ready := 0.B
      icacheValid := 0.B
      crossAddrP := ifVaddr.asUInt()(39 - 1, Offset) + 1.U
      partialInst := io.icacheIO.cpuResult.data(15, 0)
    }
  }

  if (ext('C')) when(crossCache === 1.B) {
    io.icacheIO.cpuReq.addr := crossAddr
    io.ifIO.pipelineResult.cpuResult.data := io.icacheIO.cpuResult.data(15, 0) ## partialInst
    when(icacheReady) { crossCache := 0.B }
  }

  when(ifDel && ifExcpt) { ifDel := 0.B; ifCause := 0.U; ifExcpt := 0.B; ifCrossCache := 0.B }
  when(memDel && memExcpt) {
    when(!io.jmpBch) { memDel := 0.B; memCause := 0.U; memExcpt := 0.B }
    .otherwise       { memDel := 1.B }
  }

  when(io.jmpBch && stage =/= idle && current === ifWalking && isSv39_i) {
    stage := idle
    ifDel := 0.B
    ifCause := 0.B
    ifExcpt := 0.B
    ifReady := 0.B
    icacheReady := 0.B
    ifCrossCache := 0.B
    crossCache := 0.B
    io.dcacheIO.cpuReq.revoke := 1.B
    dcacheValid := 0.B
  }
  when(io.jmpBch) { crossCache := 0.B }

  if (Debug) {
    val memAddr = Mux(isSv39_d, tlb.translate(memVaddr), memVaddr.asUInt())(alen - 1, 0)
    io.ifIO.pipelineResult.isMMIO := DontCare
    io.memIO.pipelineResult.isMMIO := memAddr < DRAM.BASE.U && memAddr >= PLIC.BASE.U || memAddr >= CLINT.BASE.U && memAddr < (CLINT.BASE + CLINT.SIZE).U
  }

  private case class IfRaiseException(cause: UInt, isPtw: Boolean = true) {
    if (isPtw) stage := idle
    if (isPtw) dcacheValid := 0.B
    ifDel   := 1.B
    ifReady := 1.B
    ifCause := cause
    ifExcpt := 1.B
    ifCrossCache := crossCache
    crossCache := 0.B
  }

  private case class MemRaiseException(cause: UInt, isPtw: Boolean = true) {
    IfRaiseException(cause, isPtw)
    memDel   := 1.B
    memReady := 1.B
    memCause := cause
    memExcpt := 1.B
  }
}

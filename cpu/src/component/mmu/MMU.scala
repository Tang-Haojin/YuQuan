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
  })

  // FIXME: disable paging in M-mode
  private val idle::walking::writing::Nil = Enum(3)
  private val ifWalking::memWalking::Nil = Enum(2)
  private val stage = RegInit(0.U(2.W))
  private val level = RegInit(0.U(2.W))
  private val tlb = new TLB
  private val ifVaddr  = io.ifIO.pipelineReq.cpuReq.addr.asTypeOf(new Vaddr)
  private val memVaddr = io.memIO.pipelineReq.cpuReq.addr.asTypeOf(new Vaddr)
  private val vaddr    = RegInit(new Vaddr, 0.U.asTypeOf(new Vaddr))
  private val satp     = UseSatp(io.satp)
  private val pte      = RegInit(new PTE, 0.U.asTypeOf(new PTE))
  private val newPte   = io.dcacheIO.cpuResult.data.asTypeOf(new PTE)
  private val current  = RegInit(0.U(1.W))
  private val isWrite  = io.memIO.pipelineReq.cpuReq.rw
  private val ptePpn   = RegInit(0.U(44.W))
  private val (ifDel  , memDel  ) = (RegInit(0.B), RegInit(0.B))
  private val (ifReady, memReady) = (RegInit(0.B), RegInit(0.B))
  private val (ifExcpt, memExcpt) = (RegInit(0.B), RegInit(0.B))
  private val (ifCause, memCause) = (RegInit(0.U(4.W)), RegInit(0.U(4.W)))

  when(ifDel) { ifDel := 0.B; ifCause := 0.U; ifExcpt := 0.B }
  when(memDel) { memDel := 0.B; memCause := 0.U; memExcpt := 0.B }

  io.icacheIO.cpuReq.data  := DontCare
  io.icacheIO.cpuReq.rw    := DontCare
  io.icacheIO.cpuReq.wmask := DontCare

  io.ifIO.pipelineResult.cause      := 0.U
  io.ifIO.pipelineResult.exception  := 0.B
  io.memIO.pipelineResult.cause     := 0.U
  io.memIO.pipelineResult.exception := 0.B

  io.ifIO.pipelineReq.cpuReq        <> io.icacheIO.cpuReq
  io.ifIO.pipelineResult.cpuResult  <> io.icacheIO.cpuResult
  io.memIO.pipelineReq.cpuReq       <> io.dcacheIO.cpuReq
  io.memIO.pipelineResult.cpuResult <> io.dcacheIO.cpuResult

  when(ifDel) {
    io.ifIO.pipelineResult.cpuResult.ready := ifReady
    io.ifIO.pipelineResult.exception := ifExcpt
    io.ifIO.pipelineResult.cause := ifCause
  }
  when(memDel) {
    io.memIO.pipelineResult.cpuResult.ready := memReady
    io.memIO.pipelineResult.exception := memExcpt
    io.memIO.pipelineResult.cause := memCause
  }

  when(satp.mode === 8.U) {
    io.icacheIO.cpuReq.addr := tlb.getPpn(ifVaddr) ## ifVaddr.offset
    io.dcacheIO.cpuReq.addr := tlb.getPpn(memVaddr) ## memVaddr.offset
    when(!tlb.isHit(ifVaddr)) {
      ifDel := 1.B
      ifReady := 0.B
      io.icacheIO.cpuReq.valid := 0.B
    }
    when(!tlb.isHit(memVaddr) || (isWrite && !tlb.isDirty(memVaddr))) {
      memDel := 1.B
      memReady := 0.B
      io.dcacheIO.cpuReq.valid := 0.B
    }

    when(stage === walking) {
      io.dcacheIO.cpuReq.valid := 1.B
      io.dcacheIO.cpuReq.rw    := 0.B
      io.dcacheIO.cpuReq.addr  := Mux(level === 2.U, satp.ppn, pte.ppn) ## vaddr.vpn(level) ## 0.U(3.W)
      io.memIO.pipelineResult.cpuResult.ready := 0.B
      when(io.dcacheIO.cpuResult.ready) {
        pte := io.dcacheIO.cpuResult.data
        when(level =/= 0.U) {
          when(newPte.w | newPte.r | newPte.x) {
            when(current === ifWalking) { IfRaiseException(1.U) } // Instruction access fault
            .otherwise { MemRaiseException(Mux(isWrite, 7.U, 5.U)) } // load/store/amo access fault
          }
          level := level - 1.U
        }.otherwise {
          stage := idle
          io.dcacheIO.cpuReq.valid := 0.B
          when((!newPte.w && !newPte.r && !newPte.x) || (newPte.w && !newPte.r)) { // this should be leaf, and that with w must have r
            when(current === ifWalking) { IfRaiseException(1.U) } // Instruction access fault
            .otherwise { MemRaiseException(Mux(isWrite, 7.U, 5.U)) } // load/store/amo access fault
          }.elsewhen(current === ifWalking && !newPte.x) { IfRaiseException(1.U) } // Instruction access fault
          .elsewhen(current === memWalking && !isWrite && !newPte.r) { MemRaiseException(5.U) } // load access fault
          .elsewhen(current === memWalking && isWrite && !newPte.w) { MemRaiseException(7.U) } // store/amo access fault
          .elsewhen(!newPte.a || (current === memWalking && isWrite && !newPte.d)) { stage := writing; ptePpn := pte.ppn }
          .otherwise { tlb.update(vaddr, newPte) }
        }
        when(!newPte.v) {
          when(current === ifWalking) { IfRaiseException(12.U) } // Instruction page fault
          .otherwise { MemRaiseException(Mux(isWrite, 15.U, 13.U)) } // load/store/amo page fault
        }
      }
    }

    when(stage === writing) {
      val writingPte = WireDefault(new PTE, pte)
      writingPte.a := 1.B
      writingPte.d := (current === memWalking && isWrite) | pte.d
      io.dcacheIO.cpuReq.valid := 1.B
      io.dcacheIO.cpuReq.addr  := ptePpn ## vaddr.vpn(0) ## 0.U(3.W)
      io.dcacheIO.cpuReq.rw    := 1.B
      io.dcacheIO.cpuReq.wmask := "b11111111".U
      io.dcacheIO.cpuReq.data  := writingPte.asUInt
      io.memIO.pipelineResult.cpuResult.ready := 0.B
      when(io.dcacheIO.cpuResult.ready) {
        tlb.update(vaddr, writingPte)
        io.dcacheIO.cpuReq.valid := 0.B
        stage := idle
      }
    }
  }

  when(io.memIO.pipelineReq.cpuReq.valid && io.memIO.pipelineReq.flush) {
    tlb.flush()
    memDel := !memDel; memReady := 1.B; memCause := 0.U; memExcpt := 0.B
    io.dcacheIO.cpuReq.valid := 0.B
  }.elsewhen(io.ifIO.pipelineReq.cpuReq.valid && io.ifIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) {
    io.icacheIO.cpuReq.valid := 0.B
    IfRaiseException(0.U, false) // Instruction address misaligned // TODO: compression instructions
  }.elsewhen(io.memIO.pipelineReq.cpuReq.valid && (
    (io.memIO.pipelineReq.reqLen === 1.U && io.memIO.pipelineReq.cpuReq.addr(0)) ||
    (io.memIO.pipelineReq.reqLen === 2.U && io.memIO.pipelineReq.cpuReq.addr(1, 0) =/= 0.U) ||
    (io.memIO.pipelineReq.reqLen === 3.U && io.memIO.pipelineReq.cpuReq.addr(2, 0) =/= 0.U)
  )) {
    io.dcacheIO.cpuReq.valid := 0.B
    MemRaiseException(Mux(isWrite, 6.U, 4.U), false) // load/store/amo address misaligned
  }.elsewhen(satp.mode === 8.U) {
    when(io.ifIO.pipelineReq.cpuReq.valid && !tlb.isHit(ifVaddr) && !io.dcacheIO.cpuReq.valid && stage === idle) {
      current := ifWalking
      stage := walking
      vaddr := ifVaddr
      level := 2.U
    }

    when(io.memIO.pipelineReq.cpuReq.valid && (!tlb.isHit(memVaddr) || (isWrite && !tlb.isDirty(memVaddr))) && stage === idle) {
      current := memWalking
      stage := walking
      vaddr := memVaddr
      level := 2.U
    }
  }

  private case class IfRaiseException(cause: UInt, isPtw: Boolean = true) {
    if (isPtw) stage := idle
    if (isPtw) io.dcacheIO.cpuReq.valid := 0.B
    ifDel   := 1.B
    ifReady := 1.B
    ifCause := cause
    ifExcpt := 1.B
  }

  private case class MemRaiseException(cause: UInt, isPtw: Boolean = true) {
    IfRaiseException(cause, isPtw)
    memDel   := 1.B
    memReady := 1.B
    memCause := cause
    memExcpt := 1.B
  }
}

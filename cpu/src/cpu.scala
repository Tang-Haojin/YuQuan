package cpu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline._
import component._
import component.mmu._
import tools._
import cache._
import utils._

class CPU(implicit p: Parameters) extends YQModule {
  override val desiredName = if (IsYsyx) modulePrefix.dropRight(1) else if (IsZmb) "SimTop" else modulePrefix + this.getClass().getSimpleName()
  val io = IO(new YQBundle {
    val master    = new AXI_BUNDLE
    val slave     = if (!IsZmb) Flipped(new AXI_BUNDLE) else null
    val interrupt = if (!IsZmb) Input(Bool())           else null
//--------------------------these are useless------------------------┐
    val logCtrl   = if (!IsZmb) null else Input(new zmb.LogCtrl)  // | these
    val perfInfo  = if (!IsZmb) null else Input(new zmb.PerfInfo) // | are
    val uart      = if (!IsZmb) null else       new zmb.Uart      // | useless
//--------------------------these are useless------------------------┘
    val debug     =
    if(Debug)       new DEBUG
    else            null
  })

  dontTouch(io)

  private val moduleGPRs      = Module(new GPRs)
  private val moduleCSRs      = Module(new cpu.privileged.M_CSRs)
  private val moduleBypass    = Module(new Bypass)
  private val moduleBypassCsr = Module(new BypassCsr)
  private val moduleAXIRMux   = Module(new AXIRMux)
  private val moduleAXIWMux   = if (useSlave) Module(new AXIWMux) else null

  private val moduleICache = ICache(p.alter(cache.CacheConfig.f))
  private val moduleDCache = DCache(p.alter(cache.CacheConfig.f))
  private val moduleMMU    = Module(new MMU()(p.alter(cache.CacheConfig.f)))
  private val moduleClint  = Module(new Clint)

  private val moduleIF  = Module(new IF)
  private val moduleID  = Module(new ID)
  private val moduleEX  = Module(new EX)
  private val moduleMEM = Module(new MEM)
  private val moduleWB  = Module(new WB)

  moduleAXIRMux.io.axiRaIn0 <> moduleICache.io.memIO.ar
  moduleAXIRMux.io.axiRaIn1 <> moduleDCache.io.memIO.ar
  moduleAXIRMux.io.axiRaOut <> io.master.ar

  moduleAXIRMux.io.axiRdIn0 <> moduleICache.io.memIO.r
  moduleAXIRMux.io.axiRdIn1 <> moduleDCache.io.memIO.r
  moduleAXIRMux.io.axiRdOut <> io.master.r

  if (useSlave && !IsZmb) {
    moduleAXIWMux.io.axiWaIn0 <> moduleDCache.io.memIO.aw
    moduleAXIWMux.io.axiWdIn0 <> moduleDCache.io.memIO.w
    moduleAXIWMux.io.axiWrIn0 <> moduleDCache.io.memIO.b

    io.master.aw <> moduleAXIWMux.io.axiWaOut
    io.master.w  <> moduleAXIWMux.io.axiWdOut
    io.master.b  <> moduleAXIWMux.io.axiWrOut

    io.slave.ar <> DontCare
    io.slave.r  <> DontCare
    io.slave.aw <> moduleAXIWMux.io.axiWaIn1
    io.slave.w  <> moduleAXIWMux.io.axiWdIn1
    io.slave.b  <> moduleAXIWMux.io.axiWrIn1
  } else {
    io.master.aw <> moduleDCache.io.memIO.aw
    io.master.w  <> moduleDCache.io.memIO.w
    io.master.b  <> moduleDCache.io.memIO.b
    if (!IsZmb) io.slave <> DontCare
  }

  moduleID.io.gprsR <> moduleBypass.io.receive
  moduleID.io.csrsR <> moduleCSRs.io.csrsR
  moduleWB.io.gprsW <> moduleGPRs.io.gprsW
  moduleWB.io.csrsW <> moduleCSRs.io.csrsW

  moduleIF.io.output  <> moduleID.io.input
  moduleID.io.output  <> moduleEX.io.input
  moduleEX.io.output  <> moduleMEM.io.input
  moduleMEM.io.output <> moduleWB.io.input

  moduleIF.io.nextVR  <> moduleID.io.lastVR
  moduleID.io.nextVR  <> moduleEX.io.lastVR
  moduleEX.io.nextVR  <> moduleMEM.io.lastVR
  moduleMEM.io.nextVR <> moduleWB.io.lastVR

  moduleMMU.io.icacheIO  <> moduleICache.io.cpuIO
  moduleMMU.io.dcacheIO  <> moduleDCache.io.cpuIO
  moduleMMU.io.ifIO      <> moduleIF.io.immu
  moduleMMU.io.memIO     <> moduleMEM.io.dmmu
  moduleMMU.io.satp      <> moduleCSRs.io.satp
  moduleMMU.io.priv      <> moduleCSRs.io.currentPriv
  moduleMMU.io.sum       <> moduleCSRs.io.sum
  moduleMMU.io.jmpBch    <> moduleID.io.jmpBch
  moduleEX.io.invIch     <> moduleICache.io.inv
  moduleEX.io.wbDch      <> moduleDCache.io.wb
  moduleID.io.jmpBch     <> moduleICache.io.jmpBch
  moduleClint.io.clintIO <> moduleDCache.io.clintIO

  moduleBypass.io.request <> moduleGPRs.io.gprsR
  moduleBypass.io.idOut.valid  := moduleID.io.nextVR.VALID
  moduleBypass.io.idOut.index  := moduleID.io.output.rd
  moduleBypass.io.idOut.value  := DontCare
  moduleBypass.io.exOut.valid  := moduleEX.io.nextVR.VALID
  moduleBypass.io.exOut.index  := moduleEX.io.output.rd
  moduleBypass.io.exOut.value  := moduleEX.io.output.data
  moduleBypass.io.memOut.valid := moduleMEM.io.nextVR.VALID
  moduleBypass.io.memOut.index := moduleMEM.io.output.rd
  moduleBypass.io.memOut.value := moduleMEM.io.output.data
  moduleBypass.io.isLd         := moduleEX.io.output.isLd
  moduleBypass.io.isAmo        := moduleID.io.isAmo

  moduleBypassCsr.io.idIO.bits   := moduleID.io.output
  moduleBypassCsr.io.idIO.valid  := moduleID.io.nextVR.VALID
  moduleBypassCsr.io.exIO.bits   := moduleEX.io.output
  moduleBypassCsr.io.exIO.valid  := moduleEX.io.nextVR.VALID
  moduleBypassCsr.io.memIO.bits  := moduleMEM.io.output
  moduleBypassCsr.io.memIO.valid := moduleMEM.io.nextVR.VALID

  moduleIF.io.isPriv := moduleBypassCsr.io.isPriv
  moduleID.io.isWait := moduleBypass.io.isWait || moduleBypassCsr.io.isWait

  moduleIF.io.jmpBch := moduleID.io.jmpBch
  moduleIF.io.jbAddr := moduleID.io.jbAddr

  moduleEX.io.seip := moduleCSRs.io.bareSEIP
  moduleEX.io.ueip := moduleCSRs.io.bareUEIP

  moduleCSRs.io.eip         <> (if (IsZmb) 0.B else io.interrupt)
  moduleCSRs.io.retire      <> moduleWB.io.retire
  moduleCSRs.io.changePriv  <> moduleWB.io.isPriv
  moduleCSRs.io.newPriv     <> moduleWB.io.priv
  moduleCSRs.io.currentPriv <> moduleID.io.currentPriv
  moduleCSRs.io.mtime       <> moduleClint.io.mtime
  moduleCSRs.io.mtip        <> moduleClint.io.mtip

  if (Debug) {
    io.debug.exit     := moduleWB.io.debug.exit
    io.debug.data     := moduleGPRs.io.gprsR.rdata(2)
    io.debug.wbPC     := moduleWB.io.debug.pc
    io.debug.wbValid  := moduleWB.io.retire
    io.debug.wbRd     := moduleWB.io.debug.rd
    io.debug.wbRcsr   := moduleWB.io.debug.rcsr
    io.debug.gprs     := moduleGPRs.io.debug.gprs
    io.debug.wbMMIO   := moduleWB.io.debug.mmio
    io.debug.wbIntr   := moduleWB.io.debug.intr
    io.debug.priv     := moduleCSRs.io.currentPriv
    io.debug.mstatus  := moduleCSRs.io.debug.mstatus
    io.debug.mepc     := moduleCSRs.io.debug.mepc
    io.debug.sepc     := moduleCSRs.io.debug.sepc
    io.debug.mtvec    := moduleCSRs.io.debug.mtvec
    io.debug.stvec    := moduleCSRs.io.debug.stvec
    io.debug.mcause   := moduleCSRs.io.debug.mcause
    io.debug.scause   := moduleCSRs.io.debug.scause
    io.debug.mie      := moduleCSRs.io.debug.mie
    io.debug.mscratch := moduleCSRs.io.debug.mscratch
  }
  if (IsZmb) {
    io.logCtrl  <> DontCare
    io.perfInfo <> DontCare
    io.uart     <> DontCare
  }
}

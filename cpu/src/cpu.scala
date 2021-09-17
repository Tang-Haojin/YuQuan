package cpu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.pipeline._
import component._
import tools._
import cache._
import utils._

class CPU(implicit p: Parameters) extends YQModule {
  override val desiredName = if (IsYsyx) modulePrefix.dropRight(1)
                             else modulePrefix + this.getClass().getSimpleName()
  val io = IO(new YQBundle {
    val master    = new AXI_BUNDLE
    val slave     = Flipped(new AXI_BUNDLE)
    val interrupt = Input(Bool())
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
  private val moduleAXIWMux   = Module(new AXIWMux)

  private val moduleICache = ICache(p.alter(cache.CacheConfig.f))
  private val moduleDCache = DCache(p.alter(cache.CacheConfig.f))

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

  moduleIF.io.icache  <> moduleICache.io.cpuIO
  moduleMEM.io.dcache <> moduleDCache.io.cpuIO
  moduleEX.io.invIch  <> moduleICache.io.inv
  moduleEX.io.wbDch   <> moduleDCache.io.wb

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

  moduleBypassCsr.io.receive := moduleID.io.csrsR.rcsr.asUInt.andR
  moduleBypassCsr.io.idOut   := moduleID.io.output.wcsr.asUInt.andR  || !moduleID.io.nextVR.VALID
  moduleBypassCsr.io.exOut   := moduleEX.io.output.wcsr.asUInt.andR  || !moduleEX.io.nextVR.VALID
  moduleBypassCsr.io.memOut  := moduleMEM.io.output.wcsr.asUInt.andR || !moduleMEM.io.nextVR.VALID

  moduleID.io.isWait := moduleBypass.io.isWait || moduleBypassCsr.io.isWait

  moduleIF.io.jmpBch := moduleID.io.jmpBch
  moduleIF.io.jbAddr := moduleID.io.jbAddr

  moduleEX.io.seip := moduleCSRs.io.bareSEIP
  moduleEX.io.ueip := moduleCSRs.io.bareUEIP

  moduleCSRs.io.eip         <> io.interrupt
  moduleCSRs.io.retire      <> moduleWB.io.retire
  moduleCSRs.io.changePriv  <> (moduleID.io.nextVR.READY && moduleID.io.nextVR.VALID)
  moduleCSRs.io.newPriv     <> moduleID.io.newPriv
  moduleCSRs.io.currentPriv <> moduleID.io.currentPriv

  if (Debug) {
    io.debug.exit     := moduleWB.io.debug.exit
    io.debug.data     := moduleGPRs.io.gprsR.rdata(2)
    io.debug.wbPC     := moduleWB.io.debug.pc
    io.debug.wbValid  := moduleWB.io.retire
    io.debug.wbRd     := moduleWB.io.debug.rd
    io.debug.wbRcsr   := moduleWB.io.debug.rcsr
    io.debug.gprs     := moduleGPRs.io.debug.gprs
    io.debug.wbMMIO   := moduleWB.io.debug.mmio
    io.debug.wbClint  := moduleWB.io.debug.clint
    io.debug.wbIntr   := moduleWB.io.debug.intr
    io.debug.priv     := moduleWB.io.debug.priv
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
}

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
  moduleID.io.csrsR <> moduleBypassCsr.io.receive
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

  moduleBypassCsr.io.request <> moduleCSRs.io.csrsR
  for (i <- 0 until RegConf.writeCsrsPort) {
    moduleBypassCsr.io.idOut.wcsr(i)  := moduleID.io.output.wcsr(i)  | Fill(12, !moduleID.io.nextVR.VALID)
    moduleBypassCsr.io.exOut.wcsr(i)  := moduleEX.io.output.wcsr(i)  | Fill(12, !moduleEX.io.nextVR.VALID)
    moduleBypassCsr.io.memOut.wcsr(i) := moduleMEM.io.output.wcsr(i) | Fill(12, !moduleMEM.io.nextVR.VALID)
  }
  moduleBypassCsr.io.idOut.value  := DontCare
  moduleBypassCsr.io.exOut.value  := moduleEX.io.output.csrData
  moduleBypassCsr.io.memOut.value := moduleMEM.io.output.csrData

  moduleID.io.isWait := moduleBypass.io.isWait || moduleBypassCsr.io.isWait

  moduleIF.io.jmpBch := moduleID.io.jmpBch
  moduleIF.io.jbAddr := moduleID.io.jbAddr

  moduleCSRs.io.eip         <> io.interrupt
  moduleCSRs.io.retire      <> moduleWB.io.retire
  moduleCSRs.io.changePriv  <> (moduleID.io.nextVR.READY && moduleID.io.nextVR.VALID)
  moduleCSRs.io.newPriv     <> moduleID.io.newPriv
  moduleCSRs.io.currentPriv <> moduleID.io.currentPriv

  if (Debug) {
    io.debug.exit    := moduleWB.io.debug.exit
    io.debug.data    := moduleGPRs.io.gprsR.rdata(2)
    io.debug.wbPC    := moduleWB.io.debug.pc
    io.debug.wbValid := moduleWB.io.retire
    io.debug.wbRd    := moduleWB.io.debug.rd
    io.debug.gprs    := moduleGPRs.io.debug.gprs
    
    moduleEX.io.input.debug.pc    := moduleID.io.output.debug.pc
    moduleMEM.io.input.debug.pc   := moduleEX.io.output.debug.pc
    moduleMEM.io.input.debug.exit := moduleEX.io.output.debug.exit
    moduleWB.io.input.debug.pc    := moduleMEM.io.output.debug.pc
    moduleWB.io.input.debug.exit  := moduleMEM.io.output.debug.exit
  }
}

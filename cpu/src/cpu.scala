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
  override val desiredName = if (modulePrefix.length() > 1) modulePrefix.dropRight(1) else modulePrefix + this.getClass().getSimpleName()
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
  private val moduleCSRs      = Module(new cpu.privileged.CSRs)
  private val moduleBypass    = Module(new Bypass)
  private val moduleBypassCsr = Module(new BypassCsr)
  private val moduleAXIRMux   = Module(new AXIRMux)
  private val moduleDCacheMux = if (useSlave) Module(new DCacheMux) else null
  private val moduleDMA       = if (useSlave) Module(new DMA) else null

  private val moduleICache = ICache()
  private val moduleDCache = DCache()
  private val moduleMMU    = Module(new MMU)
  private val moduleClint  = if (useClint) Module(new Clint) else null
  private val modulePlic   = if (usePlic) Module(new SimplePlic) else null

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

  io.master.aw <> moduleDCache.io.memIO.aw
  io.master.w  <> moduleDCache.io.memIO.w
  io.master.b  <> moduleDCache.io.memIO.b

  if (useSlave) {
    moduleDMA.io.memIO          <> io.slave
    moduleDMA.io.cpuIO          <> moduleDCacheMux.io.dmaIO
    moduleMMU.io.dcacheIO       <> moduleDCacheMux.io.cpuIO
    moduleDCacheMux.io.dcacheIO <> moduleDCache.io.cpuIO
  } else {
    moduleMMU.io.dcacheIO <> moduleDCache.io.cpuIO
    io.slave  <> DontCare
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

  moduleMMU.io.icacheIO   <> moduleICache.io.cpuIO
  moduleMMU.io.ifIO       <> moduleIF.io.immu
  moduleMMU.io.memIO      <> moduleMEM.io.dmmu
  moduleMMU.io.satp       <> moduleCSRs.io.satp
  moduleMMU.io.priv       <> moduleCSRs.io.currentPriv
  moduleMMU.io.sum        <> moduleCSRs.io.sum
  moduleMMU.io.mprv       <> moduleCSRs.io.mprv
  moduleMMU.io.mpp        <> moduleCSRs.io.mpp
  moduleMMU.io.jmpBch     <> moduleID.io.jmpBch
  moduleEX.io.invIch      <> moduleICache.io.inv
  moduleEX.io.wbDch       <> moduleDCache.io.wb
  moduleID.io.jmpBch      <> moduleICache.io.jmpBch
  moduleID.io.mtip        <> (if (useClint) moduleClint.io.mtip else 0.B)
  moduleID.io.msip        <> (if (useClint) moduleClint.io.msip else 0.B)
  moduleDCache.io.clintIO <> (if (useClint) moduleClint.io.clintIO else DontCare)
  moduleDCache.io.plicIO  <> (if (usePlic) modulePlic.io.plicIO else DontCare)
  if (usePlic) modulePlic.io.int <> io.interrupt

  moduleBypass.io.rregs  <> moduleGPRs.io.rregs
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
  moduleBypass.io.instr        := moduleIF.io.output.instr

  moduleBypassCsr.io.idIO.bits   := moduleID.io.output
  moduleBypassCsr.io.idIO.valid  := moduleID.io.nextVR.VALID
  moduleBypassCsr.io.exIO.bits   := moduleEX.io.output
  moduleBypassCsr.io.exIO.valid  := moduleEX.io.nextVR.VALID
  moduleBypassCsr.io.memIO.bits  := moduleMEM.io.output
  moduleBypassCsr.io.memIO.valid := moduleMEM.io.nextVR.VALID

  moduleIF.io.isPriv := moduleBypassCsr.io.isPriv
  moduleIF.io.isSatp := moduleBypassCsr.io.isSatp
  moduleID.io.isWait := moduleBypass.io.isWait || moduleBypassCsr.io.isWait
  moduleID.io.revAmo := moduleMMU.io.revAmo

  moduleIF.io.jmpBch := moduleID.io.jmpBch
  moduleIF.io.jbAddr := moduleID.io.jbAddr

  moduleEX.io.seip := moduleCSRs.io.bareSEIP
  moduleEX.io.ueip := moduleCSRs.io.bareUEIP

  moduleCSRs.io.eip         <> (if (usePlic) modulePlic.io.eip else 0.B)
  moduleCSRs.io.retire      <> moduleWB.io.retire
  moduleCSRs.io.changePriv  <> moduleWB.io.isPriv
  moduleCSRs.io.newPriv     <> moduleWB.io.priv
  moduleCSRs.io.currentPriv <> moduleID.io.currentPriv
  moduleCSRs.io.mtime       <> (if (useClint) moduleClint.io.mtime else DontCare)
  moduleCSRs.io.mtip        <> (if (useClint) moduleClint.io.mtip else 0.B)
  moduleCSRs.io.msip        <> (if (useClint) moduleClint.io.msip else 0.B)

  if (Debug) {
    io.debug.exit     := moduleWB.io.debug.exit
    io.debug.wbPC     := moduleWB.io.debug.pc
    io.debug.wbValid  := moduleWB.io.retire
    io.debug.wbRd     := moduleWB.io.debug.rd
    io.debug.wbRcsr   := moduleWB.io.debug.rcsr
    io.debug.gprs     := moduleGPRs.io.debug.gprs
    io.debug.wbMMIO   := moduleWB.io.debug.mmio
    io.debug.wbIntr   := moduleWB.io.debug.intr
    io.debug.wbRvc    := moduleWB.io.debug.rvc
    io.debug.priv     := moduleCSRs.io.currentPriv
    io.debug.mstatus  := moduleCSRs.io.debug.mstatus
    io.debug.mepc     := moduleCSRs.io.debug.mepc
    io.debug.sepc     := moduleCSRs.io.debug.sepc
    io.debug.mtvec    := moduleCSRs.io.debug.mtvec
    io.debug.stvec    := moduleCSRs.io.debug.stvec
    io.debug.mcause   := moduleCSRs.io.debug.mcause
    io.debug.scause   := moduleCSRs.io.debug.scause
    io.debug.mtval    := moduleCSRs.io.debug.mtval
    io.debug.stval    := moduleCSRs.io.debug.stval
    io.debug.mie      := moduleCSRs.io.debug.mie
    io.debug.mscratch := moduleCSRs.io.debug.mscratch
  }
}

package cpu

import chisel3._
import chisel3.util._

import cpu.pipeline._
import component._
import tools._
import config.Debug._
import config.GeneralConfig._
import config.RegisterConfig._
import cache._

class DEBUG extends Bundle {
  val exit    = Output(UInt(3.W))
  val data    = Output(UInt(XLEN.W))
  val wbPC    = Output(UInt(XLEN.W))
  val wbValid = Output(Bool())
  val wbRd    = Output(UInt(5.W))
  val gprs    = Output(Vec(32, UInt(XLEN.W)))
}

class CPU extends RawModule {
  val io = IO(new Bundle {
    val basic   = new BASIC
    val memAXI  = new AxiMasterChannel
    val dmaAXI  = Flipped(new AxiMasterChannel)
    val intr    = Input(Bool())
    val debug   = 
    if(Debug)     new DEBUG
    else          null
  })

  dontTouch(io)

  withClockAndReset(io.basic.ACLK, !io.basic.ARESETn) {
    val cpu = Module(new InternalCPU)
    io.intr    <> cpu.io.intr
    io.memAXI  <> cpu.io.memAXI
    io.dmaAXI  <> cpu.io.dmaAXI
    if (Debug) io.debug <> cpu.io.debug
  }
}

class InternalCPU extends Module {
  val io = IO(new Bundle {
    val memAXI  = new AxiMasterChannel
    val dmaAXI  = Flipped(new AxiMasterChannel)
    val intr    = Input(Bool())
    val debug   = 
    if(Debug)     new DEBUG
    else          null
  })

  val moduleGPRs      = Module(new GPRs)
  val moduleCSRs      = Module(new cpu.privileged.M_CSRs)
  val moduleBypass    = Module(new Bypass)
  val moduleBypassCsr = Module(new BypassCsr)
  val moduleAXIRMux   = Module(new AXIRMux)

  val moduleICache = ICache()
  val moduleDCache = Module(new DCache)

  val moduleIF  = Module(new IF)
  val moduleID  = Module(new ID)
  val moduleEX  = Module(new EX)
  val moduleMEM = Module(new MEM)
  val moduleWB  = Module(new WB)

  moduleAXIRMux.io.axiRaIn0 <> moduleICache.io.memIO.axiRa
  moduleAXIRMux.io.axiRaIn1 <> moduleDCache.io.memIO.axiRa
  moduleAXIRMux.io.axiRaOut <> io.memAXI.axiRa

  moduleAXIRMux.io.axiRdIn0 <> moduleICache.io.memIO.axiRd
  moduleAXIRMux.io.axiRdIn1 <> moduleDCache.io.memIO.axiRd
  moduleAXIRMux.io.axiRdOut <> io.memAXI.axiRd

  io.memAXI.axiWa <> moduleDCache.io.memIO.axiWa
  io.memAXI.axiWd <> moduleDCache.io.memIO.axiWd
  io.memAXI.axiWr <> moduleDCache.io.memIO.axiWr

  io.dmaAXI := DontCare

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

  moduleBypass.io.request <> moduleGPRs.io.gprsR
  moduleBypass.io.idOut.index  := moduleID.io.output.rd & Fill(5, moduleID.io.nextVR.VALID)
  moduleBypass.io.idOut.value  := DontCare
  moduleBypass.io.exOut.index  := moduleEX.io.output.rd & Fill(5, moduleEX.io.nextVR.VALID)
  moduleBypass.io.exOut.value  := moduleEX.io.output.data
  moduleBypass.io.memOut.index := moduleMEM.io.output.rd & Fill(5, moduleMEM.io.nextVR.VALID)
  moduleBypass.io.memOut.value := moduleMEM.io.output.data
  moduleBypass.io.isLd         := moduleEX.io.output.isLd

  moduleBypassCsr.io.request <> moduleCSRs.io.csrsR
  for (i <- 0 until writeCsrsPort) {
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

  moduleCSRs.io.eip <> io.intr

  if (Debug) {
    io.debug.exit    := moduleWB.io.debug.exit
    io.debug.data    := moduleGPRs.io.gprsR.rdata(2)
    io.debug.wbPC    := moduleWB.io.debug.pc
    io.debug.wbValid := moduleWB.io.debug.wbvalid
    io.debug.wbRd    := moduleWB.io.debug.rd
    io.debug.gprs    := moduleGPRs.io.debug.gprs
    
    moduleEX.io.input.debug.pc    := moduleID.io.output.debug.pc
    moduleMEM.io.input.debug.pc   := moduleEX.io.output.debug.pc
    moduleMEM.io.input.debug.exit := moduleEX.io.output.debug.exit
    moduleWB.io.input.debug.pc    := moduleMEM.io.output.debug.pc
    moduleWB.io.input.debug.exit  := moduleMEM.io.output.debug.exit
  }

  if (showReg) {
    moduleGPRs.io.debug.showReg := moduleWB.io.debug.showReg
  }
}

package cpu

import chisel3._
import chisel3.util._

import cpu.register._
import cpu.axi._
import cpu.config.Debug._
import cpu.config.GeneralConfig._

class DEBUG extends Bundle {
  val exit = Output(UInt(3.W))
  val data = Output(UInt(XLEN.W))
  val pc   = Output(UInt(XLEN.W))
  val wbPC = if (DiffTest) Output(UInt(XLEN.W)) else null
}

class CPU extends RawModule {
	val io = IO(new Bundle {
    val basic = new BASIC
    val axiWa = new AXIwa
    val axiWd = new AXIwd
    val axiWr = new AXIwr
    val axiRa = new AXIra
    val axiRd = new AXIrd
    val debug = 
    if(Debug)   new DEBUG
    else        null
  })

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val cpu = Module(new InternalCPU)
    io.axiWa <> cpu.io.axiWa
    io.axiWd <> cpu.io.axiWd
    io.axiWr <> cpu.io.axiWr
    io.axiRa <> cpu.io.axiRa
    io.axiRd <> cpu.io.axiRd
    if (Debug) io.debug <> cpu.io.debug
  }
}

class InternalCPU extends Module {
	val io = IO(new Bundle {
    val axiWa = new AXIwa
    val axiWd = new AXIwd
    val axiWr = new AXIwr
    val axiRa = new AXIra
    val axiRd = new AXIrd
    val debug = 
    if(Debug)   new DEBUG
    else        null
  })

  val modulePC      = Module(new PC)
  val moduleGPRs    = Module(new GPRs)
  val moduleBypass  = Module(new Bypass)
  val moduleAXIRMux = Module(new AXIRMux)

  val moduleIF  = Module(new IF)
  val moduleID  = Module(new ID)
  val moduleEX  = Module(new EX)
  val moduleMEM = Module(new MEM)
  val moduleWB  = Module(new WB)

  moduleIF.io.axiRa  <> moduleAXIRMux.io.axiRaIn0
  moduleMEM.io.axiRa <> moduleAXIRMux.io.axiRaIn1
  io.axiRa           <> moduleAXIRMux.io.axiRaOut

  moduleIF.io.axiRd  <> moduleAXIRMux.io.axiRdIn0
  moduleMEM.io.axiRd <> moduleAXIRMux.io.axiRdIn1
  io.axiRd           <> moduleAXIRMux.io.axiRdOut

  io.axiWa <> moduleMEM.io.axiWa
  io.axiWd <> moduleMEM.io.axiWd
  io.axiWr <> moduleMEM.io.axiWr

  moduleIF.io.pcIo  <> modulePC.io.pcIo

  moduleID.io.gprsR <> moduleBypass.io.receive
  moduleWB.io.gprsW <> moduleGPRs.io.gprsW

  moduleIF.io.output  <> moduleID.io.input
  moduleID.io.output  <> moduleEX.io.input
  moduleEX.io.output  <> moduleMEM.io.input
  moduleMEM.io.output <> moduleWB.io.input

  moduleIF.io.nextVR  <> moduleID.io.lastVR
  moduleID.io.nextVR  <> moduleEX.io.lastVR
  moduleEX.io.nextVR  <> moduleMEM.io.lastVR
  moduleMEM.io.nextVR <> moduleWB.io.lastVR

  moduleBypass.io.request <> moduleGPRs.io.gprsR
  moduleBypass.io.idOut.index  := moduleID.io.output.rd & Fill(5, moduleID.io.nextVR.VALID.asUInt)
  moduleBypass.io.idOut.value  := DontCare
  moduleBypass.io.exOut.index  := moduleEX.io.output.rd & Fill(5, moduleEX.io.nextVR.VALID.asUInt)
  moduleBypass.io.exOut.value  := moduleEX.io.output.data
  moduleBypass.io.memOut.index := moduleMEM.io.output.rd & Fill(5, moduleMEM.io.nextVR.VALID.asUInt)
  moduleBypass.io.memOut.value := moduleMEM.io.output.data
  moduleBypass.io.isLd         := moduleEX.io.output.isLd
  moduleID.io.isWait := moduleBypass.io.isWait

  moduleIF.io.jmpBch := moduleID.io.jmpBch
  moduleIF.io.jbAddr := moduleID.io.jbAddr

  if (Debug) {
    io.debug.exit := moduleEX.io.output.exit
    io.debug.data := moduleEX.io.output.data
    io.debug.pc   := modulePC.io.pcIo.rdata
  }

  if (DiffTest) {
    io.debug.wbPC := moduleWB.io.debug.pc
    moduleEX.io.input.debug.pc  := moduleID.io.output.debug.pc
    moduleMEM.io.input.debug.pc := moduleEX.io.output.debug.pc
    moduleWB.io.input.debug.pc  := moduleMEM.io.output.debug.pc
  }

  if (showReg) {
    moduleGPRs.io.debug.showReg := (moduleWB.io.debug.showReg)
    modulePC.io.debug.showReg   := (moduleWB.io.debug.showReg)
  }
}

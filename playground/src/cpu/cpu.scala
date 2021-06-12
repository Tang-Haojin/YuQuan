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
    if(debugIO) new DEBUG
    else        null
  })

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val cpu = Module(new InternalCPU)
    io.axiWa <> cpu.io.axiWa
    io.axiWd <> cpu.io.axiWd
    io.axiWr <> cpu.io.axiWr
    io.axiRa <> cpu.io.axiRa
    io.axiRd <> cpu.io.axiRd
    if (debugIO) io.debug <> cpu.io.debug
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
    if(debugIO) new DEBUG
    else        null
  })

  val modulePC       = Module(new PC)
  val moduleGPRs     = Module(new GPRs)
  val moduleAXIRaMux = Module(new AXIRaMux)
  val moduleAXIRdMux = Module(new AXIRdMux)

  val moduleIF  = Module(new IF)
  val moduleID  = Module(new ID)
  val moduleEX  = Module(new EX)
  val moduleMEM = Module(new MEM)
  val moduleWB  = Module(new WB)

  moduleIF.io.axiRa  <> moduleAXIRaMux.io.axiRaIn0
  moduleMEM.io.axiRa <> moduleAXIRaMux.io.axiRaIn1
  io.axiRa           <> moduleAXIRaMux.io.axiRaOut

  moduleIF.io.axiRd  <> moduleAXIRdMux.io.axiRdIn0
  moduleMEM.io.axiRd <> moduleAXIRdMux.io.axiRdIn1
  io.axiRd           <> moduleAXIRdMux.io.axiRdOut

  io.axiWa <> moduleMEM.io.axiWa
  io.axiWd <> moduleMEM.io.axiWd
  io.axiWr <> moduleMEM.io.axiWr

  moduleIF.io.pcIo  <> modulePC.io.pcIo
  moduleID.io.pcIo  <> modulePC.io.pcIo

  moduleID.io.gprsR <> moduleGPRs.io.gprsR
  moduleWB.io.gprsW <> moduleGPRs.io.gprsW

  moduleIF.io.instr   <> moduleID.io.instr
  moduleID.io.output  <> moduleEX.io.input
  moduleEX.io.output  <> moduleMEM.io.input
  moduleMEM.io.output <> moduleWB.io.input

  moduleIF.io.nextVR  <> moduleID.io.lastVR
  moduleID.io.nextVR  <> moduleEX.io.lastVR
  moduleEX.io.nextVR  <> moduleMEM.io.lastVR
  moduleMEM.io.nextVR <> moduleWB.io.lastVR
  moduleWB.io.nextVR  <> moduleIF.io.lastVR

  if (debugIO) {
    io.debug.exit := moduleEX.io.output.exit
    io.debug.data := moduleEX.io.output.data
    io.debug.pc   := modulePC.io.pcIo.rdata
  }

  if (showReg) {
    moduleGPRs.io.debug.showReg := (moduleWB.io.nextVR.READY && moduleWB.io.nextVR.VALID)
    modulePC.io.debug.showReg   := (moduleWB.io.nextVR.READY && moduleWB.io.nextVR.VALID)
  }
}
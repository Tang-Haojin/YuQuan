package cpu

import chisel3._
import chisel3.util._
import cpu.register._
import cpu.axi._

class CPU extends RawModule {
	val io = IO(new Bundle {
    val basic = new BASIC
    val axiWa = new AXIwa
    val axiWd = new AXIwd
    val axiWr = new AXIwr
    val axiRa = new AXIra
    val axiRd = new AXIrd
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

  io.basic <> moduleAXIRaMux.io.basic
  io.basic <> moduleAXIRdMux.io.basic
  io.basic <> moduleIF.io.basic
  io.basic <> moduleID.io.basic
  io.basic <> moduleEX.io.exBasic
  io.basic <> moduleMEM.io.basic
  io.basic <> moduleWB.io.basic

  moduleIF.io.axiRa  <> moduleAXIRaMux.io.axiRaIn0
  moduleMEM.io.axiRa <> moduleAXIRaMux.io.axiRaIn1
  io.axiRa           <> moduleAXIRaMux.io.axiRaOut

  moduleIF.io.axiRd  <> moduleAXIRdMux.io.axiRdIn0
  moduleMEM.io.axiRd <> moduleAXIRdMux.io.axiRdIn1
  io.axiRd           <> moduleAXIRdMux.io.axiRdOut

  io.axiWa <> moduleMEM.io.axiWa
  io.axiWd <> moduleMEM.io.axiWd
  io.axiWr <> moduleMEM.io.axiWr

  moduleIF.io.pcIo  <> modulePC.io

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
}
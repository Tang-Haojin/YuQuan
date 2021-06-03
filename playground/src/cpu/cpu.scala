package cpu

import chisel3._
import chisel3.util._
import cpu.register._
import cpu.axi._

class CPU extends RawModule {
	val io = IO(new Bundle {
    val topBasic = new BASIC
    val topAxiWa = new AXIwa
    val topAxiWd = new AXIwd
    val topAxiWr = new AXIwr
    val topAxiRa = new AXIra
    val topAxiRd = new AXIrd
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

  io.topBasic <> moduleAXIRaMux.io.muxRaBasic
  io.topBasic <> moduleAXIRdMux.io.muxRdBasic
  io.topBasic <> moduleIF.io.ifBasic
  io.topBasic <> moduleID.io.idBasic
  io.topBasic <> moduleEX.io.exBasic
  io.topBasic <> moduleMEM.io.memBasic
  io.topBasic <> moduleWB.io.wbBasic

  moduleIF.io.ifAxiRa   <> moduleAXIRaMux.io.muxAxiRaIn0
  moduleMEM.io.memAxiRa <> moduleAXIRaMux.io.muxAxiRaIn1
  io.topAxiRa           <> moduleAXIRaMux.io.muxAxiRaOut

  moduleIF.io.ifAxiRd   <> moduleAXIRdMux.io.muxAxiRdIn0
  moduleMEM.io.memAxiRd <> moduleAXIRdMux.io.muxAxiRdIn1
  io.topAxiRd           <> moduleAXIRdMux.io.muxAxiRdOut

  io.topAxiWa <> moduleMEM.io.memAxiWa
  io.topAxiWd <> moduleMEM.io.memAxiWd
  io.topAxiWr <> moduleMEM.io.memAxiWr

  moduleIF.io.ifPcIo  <> modulePC.io
  moduleIF.io.instr   <> moduleID.io.instr

  moduleID.io.idGprsR <> moduleGPRs.io.gprsR
  moduleWB.io.wbGprsW <> moduleGPRs.io.gprsW

  moduleID.io.idData  <> moduleEX.io.exData

  moduleIF.io.ifNextVR   <> moduleID.io.idLastVR
  moduleID.io.idNextVR   <> moduleEX.io.exLastVR
  moduleEX.io.exNextVR   <> moduleMEM.io.memLastVR
  moduleMEM.io.memNextVR <> moduleWB.io.wbLastVR
  moduleWB.io.wbNextVR   <> moduleIF.io.ifLastVR
}
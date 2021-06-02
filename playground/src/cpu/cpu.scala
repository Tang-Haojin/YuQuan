package cpu

import chisel3._
import chisel3.util._

class CPU extends Module {
  import cpu.axi._
	val io = IO(new AXIall)

  val IF = new IF
  
  // ???
}
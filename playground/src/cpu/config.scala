package cpu.config

import chisel3._

object GeneralConfig {
  val AluTypeWidth = 5
  val XLEN = sys.env.getOrElse("XLEN", 64).toString.toInt
  val HasRVM = true
  val MEMBase = 0x80100000L
  val MEMSize = 90 * 1024 * 1024
  val Extensions = List('I', 'M')
  val privilegeMode = if (Extensions.contains('S')) 3
                 else if (Extensions.contains('U')) 2
                 else if (Extensions.contains('M')) 1
                 else 1

  val IALIGN = 32 // compressed instructions are not implemented yet
  val ILEN = 32 // base instruction set supported only

  object UART0_MMIO {
    val UART0_BASE     = 0x10000000L
    val UART0_WADDR    = UART0_BASE
    val UART0_RADDR    = UART0_BASE + 1L
    val UART0_STATE    = UART0_BASE + 2L
    val UART0_FREE     = 0x00
    val UART0_READBUSY = 0x01
    val UART0_SIZE     = 100
  }
}

object RegisterConfig {
  val readPortsNum = 3
}

object Debug {
  val Debug      = true
  val debugIO    = Debug && false
  val showReg    = Debug && false
  val partialReg = Debug && true
  val DiffTest   = Debug && (sys.env.getOrElse("DIFF", 1).toString.toInt >= 1)
  val showRegList
    = List(false, true, true, false, false, false, false, false,
           true, false, true, false, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false)
}

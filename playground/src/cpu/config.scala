package cpu.config

object GeneralConfig {
  val AluTypeWidth = 5
  val XLEN = 64
  val MEMBase = 0x80100000L
}

object RegisterConfig {
  val readPortsNum = 3
}

object Debug {
  val debugIO     = true
  val showReg     = false
  val partialReg  = false
  val showRegList
    = List(false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false)
}
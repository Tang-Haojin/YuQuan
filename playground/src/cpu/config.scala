package cpu.config

object GeneralConfig {
  val AluTypeWidth = 4
  val XLEN = 64
  val MEMBase = 0x80100000L
}

object RegisterConfig {
  val readPortsNum = 3
}

object Debug {
  val debugIO     = true
  val showReg     = true
  val partialReg  = true
  val showRegList
    = List(false, true , true , false, false, false, false, false,
           true , false, true , false, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false)
}
package cpu.config

object GeneralConfig {
  val AluTypeWidth = 4
  val XLEN = 64
}

object RegisterConfig {
  val readPortsNum = 2
}

object Debug {
  val debugIO     = true
  val showReg     = true
  val partialReg  = true
  val showRegList
    = List(false, false, false, false, false, false, false, false,
           true , false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, false, false, false, false, false, false, false)
}
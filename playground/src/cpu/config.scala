package cpu.config
import meta.PreProc._
import meta.Booleans._

object GeneralConfig {
  val AluTypeWidth = 4

  type RV32M = TRUE
  type RV64I = TRUE
  
  IF[RV64I] {
    type RV64M = TRUE
    val XLEN = 64
  }
  
  IF[![RV64I]#v] {
    type RV64M = FALSE
    val XLEN = 32
  }
}

object RegisterConfig {
  val readPortsNum = 2
}
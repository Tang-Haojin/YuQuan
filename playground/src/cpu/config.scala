package cpu.config
import meta.PreProc._
import meta.Booleans._

object GeneralConfig {
  val XLEN = 64
  val AluTypeWidth = 4

  type RV32M = TRUE
  type RV64I = TRUE
  
  IF[RV64I] {
    type RV64M = TRUE
  }
}

object RegisterConfig {
  val readPortsNum = 3
}
package cpu.config
import meta.PreProc._
import meta.Booleans._

object GeneralConfig {
  val AluTypeWidth = 4

  type RV32M = TRUE
  type RV64I = TRUE
  type RV64M = TRUE
  val XLEN = 64
}

object RegisterConfig {
  val readPortsNum = 2
}
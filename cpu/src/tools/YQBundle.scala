package cpu.tools

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.CPUParams

abstract class YQBundle(implicit val p: Parameters) extends Bundle with CPUParams

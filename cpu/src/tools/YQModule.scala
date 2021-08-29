package cpu.tools

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.CPUParams
import utils._

abstract class YQModule(implicit p: Parameters) extends PrefixModule with CPUParams

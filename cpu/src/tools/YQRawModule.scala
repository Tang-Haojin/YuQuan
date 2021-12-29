package cpu.tools

import chipsalliance.rocketchip.config._

import cpu.CPUParams
import utils.PrefixRawModule

abstract class YQRawModule(implicit p: Parameters) extends PrefixRawModule with CPUParams

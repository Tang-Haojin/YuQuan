package cpu.tools

import chipsalliance.rocketchip.config._

import cpu.CPUParams
import utils._

abstract class YQModule(implicit p: Parameters) extends PrefixModule with CPUParams

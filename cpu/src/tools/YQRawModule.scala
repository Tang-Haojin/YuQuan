package cpu.tools

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.CPUParams
import utils.PrefixRawModule

abstract class YQRawModule(implicit p: Parameters) extends PrefixRawModule with CPUParams

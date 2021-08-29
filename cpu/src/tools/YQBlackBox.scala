package cpu.tools

import chisel3._
import chisel3.util._
import chisel3.experimental.Param

import chipsalliance.rocketchip.config._
import cpu.CPUParams

abstract class YQBlackBox(params: Map[String, Param] = Map.empty[String, Param])(implicit val p: Parameters) extends BlackBox(params) with CPUParams

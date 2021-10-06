package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

abstract class PrefixModule(implicit val p: Parameters) extends Module with PrefixParams {
  implicit class Tuple2Assign[S <: Data, T <: Data](x: (S, T)) {
    def :=(that: (S, T)): Unit = { x._1 := that._1; x._2 := that._2 }
  }
}

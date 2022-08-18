package utils

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

abstract class PrefixModule(implicit val p: Parameters) extends Module with PrefixParams {
  implicit val implReset = reset.asBool

  implicit class b39ca268[S <: Data, T <: Data](x: (S, T)) {
    def :=(that: (S, T)): Unit = { x._1 := that._1; x._2 := that._2 }
  }

  implicit class a3c809f7[T <: Data](in: T) {
    def countHigh: UInt =
      if (in.getWidth == 1) in.asUInt() else VecInit(in.asUInt().asBools().map(_.asUInt)).reduceTree(_ +& _)
    def countLow: UInt =
      if (in.getWidth == 1) ~in.asUInt() else VecInit(in.asUInt().asBools().map(~_.asUInt)).reduceTree(_ +& _)
  }

  def IndexofMax(in: Seq[(UInt, UInt)]): UInt =
    if (in.length == 2) Mux(in(0)._2 > in(1)._2, in(0)._1, in(1)._1)
    else Mux(in(0)._2 > in(1)._2, IndexofMax(in.filterNot(_ == in(1))), IndexofMax(in.filterNot(_ == in(0))))

  def IndexofMin(in: Seq[(UInt, UInt)]): UInt =
    if (in.length == 2) Mux(in(0)._2 > in(1)._2, in(1)._1, in(0)._1)
    else Mux(in(0)._2 > in(1)._2, IndexofMax(in.filterNot(_ == in(0))), IndexofMax(in.filterNot(_ == in(1))))
}

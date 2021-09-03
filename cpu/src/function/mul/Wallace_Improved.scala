package cpu.function.mul

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class Wallace_Improved(length: Int = 128)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new Wallace_ImprovedIO(length))
  private val CSAs = List.tabulate(8)(x => CSA(length))
  private val _42s = List.tabulate(4)(x => _42_Compressor(length))

  for (i <- 0 until 3)
    for (j <- 0 until 2) {
      for (k <- 0 until 2)
        _42s(i).io.input(2 * j + k) := CSAs(2 * i + j).io.output(k)
      for (k <- 0 until 3)
        CSAs(2 * i + j).io.input(k) := io.input(3 * (2 * i + j) + k)
    }

  CSAs(6).io.input(0) := _42s(0).io.output(0)
  CSAs(6).io.input(1) := _42s(0).io.output(1)
  CSAs(6).io.input(2) := _42s(1).io.output(0)
  CSAs(7).io.input(0) := _42s(1).io.output(1)
  CSAs(7).io.input(1) := _42s(2).io.output(0)
  CSAs(7).io.input(2) := _42s(2).io.output(1)

  _42s(3).io.input(0) := CSAs(6).io.output(0)
  _42s(3).io.input(1) := CSAs(6).io.output(1)
  _42s(3).io.input(2) := CSAs(7).io.output(0)
  _42s(3).io.input(3) := CSAs(7).io.output(1)

  io.output(0) := _42s(3).io.output(0)
  io.output(1) := _42s(3).io.output(1)
}

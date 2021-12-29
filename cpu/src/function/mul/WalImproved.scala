package cpu.function.mul

import chipsalliance.rocketchip.config.Parameters
import cpu.tools._

class WalImproved(length: Int = 128)(implicit p: Parameters) extends YQRawModule {
  val io = IO(new WalImprovedIO(length))
  private val CSAs   = List.fill(8)(CSA(length))
  private val comp42s = List.fill(4)(Compressor_42(length))

  for (i <- 0 until 3)
    for (j <- 0 until 2) {
      for (k <- 0 until 2)
        comp42s(i).io.input(2 * j + k) := CSAs(2 * i + j).io.output(k)
      for (k <- 0 until 3)
        CSAs(2 * i + j).io.input(k) := io.input(3 * (2 * i + j) + k)
    }

  CSAs(6).io.input(0) := comp42s(0).io.output(0)
  CSAs(6).io.input(1) := comp42s(0).io.output(1)
  CSAs(6).io.input(2) := comp42s(1).io.output(0)
  CSAs(7).io.input(0) := comp42s(1).io.output(1)
  CSAs(7).io.input(1) := comp42s(2).io.output(0)
  CSAs(7).io.input(2) := comp42s(2).io.output(1)

  comp42s(3).io.input(0) := CSAs(6).io.output(0)
  comp42s(3).io.input(1) := CSAs(6).io.output(1)
  comp42s(3).io.input(2) := CSAs(7).io.output(0)
  comp42s(3).io.input(3) := CSAs(7).io.output(1)

  io.output(0) := comp42s(3).io.output(0)
  io.output(1) := comp42s(3).io.output(1)
}

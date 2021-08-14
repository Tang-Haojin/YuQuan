import chisel3._
import chisel3.tester._

import cpu.component._

import utest._

object ALUtest extends ChiselUtestTester {
  val aList = List(-1, 0, 1, 2, 3, 0xff, 0xffffffff, 0xffffffffffffffffL)
  val bList = List(0, 1, 2, 3, 4, 5, 6)
  val testValues = for {
    op <- Operators.operators
    a <- aList
    b <- bList
  } yield (op, a, b)

  def refALU(op: UInt, a: Long, b: Int): Long = {
    op match {
      case Operators.add => a + b
      case Operators.sub => a - b
      case Operators.and => a & b
      case Operators.or  => a | b
      case Operators.xor => a ^ b
      case Operators.sll => a << b
      case Operators.sra => a >> b
      case Operators.srl => a >>> b
      case _             => 0xbad
    }
  }

  val tests = Tests {
    test("ALU") {
      testCircuit(new ALU) { c =>
        testValues.foreach { i =>
          c.io.op.poke(i._1)
          c.io.a.poke(i._2.S)
          c.io.b.poke(i._3.S)
          c.io.res.expect(refALU(i._1, i._2, i._3).S)
        }
      }
    }
  }
}

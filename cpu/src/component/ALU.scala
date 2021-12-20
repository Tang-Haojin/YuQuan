package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu.tools._
import cpu.function.mul._
import cpu.function.div._

class ALU(implicit p: Parameters) extends YQModule {
  val io = IO(new YQBundle {
    val input = Flipped(Decoupled(new YQBundle {
      val op   = UInt(Operators.quantity.W)
      val a    = SInt(xlen.W)
      val b    = SInt(xlen.W)
      val word = Bool()
      val sign = UInt(2.W)
    }))
    val output = Decoupled(SInt(xlen.W))
  })
  import Operators._
  private val a = io.input.bits.a
  private val b = io.input.bits.b

  private val result = WireDefault(SInt(xlen.W), io.input.bits.a)
  io.output.bits := result

  private val isMul = (io.input.bits.op === mul) || (io.input.bits.op === mulh)
  private val mulTop = Module(new MulTop)
  mulTop.io.input.bits.data(0) := a.asUInt
  mulTop.io.input.bits.data(1) := b.asUInt
  mulTop.io.input.bits.sign(0) := io.input.bits.sign(0)
  mulTop.io.input.bits.sign(1) := io.input.bits.sign(1)
  mulTop.io.input.valid  := isMul && io.input.valid
  mulTop.io.output.ready := io.output.ready

  private val isDiv = (io.input.bits.op === div) || (io.input.bits.op === divw) || (io.input.bits.op === divu) || (io.input.bits.op === duw)
  private val isRem = (io.input.bits.op === rem) || (io.input.bits.op === remw) || (io.input.bits.op === remu) || (io.input.bits.op === ruw)
  private val isSgn = (io.input.bits.op === rem) || (io.input.bits.op === remw) || (io.input.bits.op === divw) || (io.input.bits.op === div)
  private val divTop = Module(new DivTop)
  divTop.io.input.bits.dividend := a.asUInt
  divTop.io.input.bits.divisor  := b.asUInt
  if (xlen == 64) when(io.input.bits.word) {
    divTop.io.input.bits.dividend := Fill(xlen - 32, (a(31) & isSgn)) ## a(31, 0)
    divTop.io.input.bits.divisor  := Fill(xlen - 32, (b(31) & isSgn)) ## b(31, 0)
  }
  divTop.io.input.bits.issigned := isSgn
  divTop.io.input.valid  := (isDiv || isRem) && io.input.valid
  divTop.io.output.ready := io.output.ready

  io.input.ready  := mulTop.io.input.ready && divTop.io.input.ready
  io.output.valid := 1.B

  when(mulTop.io.input.fire() || divTop.io.input.fire()) { io.output.valid := 0.B }
  when(!mulTop.io.input.ready) { io.output.valid := mulTop.io.output.valid }
  when(!divTop.io.input.ready) { io.output.valid := divTop.io.output.valid }

  private val shiftness = if (xlen != 32) Mux(io.input.bits.word, b(4, 0), b(5, 0)) else b(4, 0)
  private val sl = a.asUInt() << shiftness
  private val (lessthan, ulessthan) = (a < b, a.asUInt() < b.asUInt())
  private val operates = Seq(
    nop  -> a,
    add  -> (a + b),
    sub  -> (a - b),
    and  -> (a & b),
    or   -> (a | b),
    xor  -> (a ^ b),
    sll  -> (sl(xlen - 1, 0).asSInt),
    sra  -> (a >> (if (xlen == 64) b(5, 0) else b(4, 0))),
    srl  -> ((a.asUInt >> (if (xlen == 64) b(5, 0) else b(4, 0))).asSInt),
    lts  -> (0.U((xlen - 1).W) ## lessthan).asSInt,
    ltu  -> (0.U((xlen - 1).W) ## ulessthan).asSInt,
    mul  -> (mulTop.io.output.bits(xlen - 1, 0).asSInt),
    rem  -> (divTop.io.output.bits.remainder.asSInt),
    div  -> (divTop.io.output.bits.quotient.asSInt),
    remu -> (divTop.io.output.bits.remainder.asSInt),
    divu -> (divTop.io.output.bits.quotient.asSInt),
    mulh -> (mulTop.io.output.bits(2 * xlen - 1, xlen).asSInt)) ++ (if (ext('A')) Seq(
    max  -> Mux(lessthan, b, a),
    min  -> Mux(lessthan, a, b),
    maxu -> Mux(ulessthan, b, a),
    minu -> Mux(ulessthan, a, b)) else Nil) ++ (if (xlen == 64) Seq(
    sllw -> (sl(31, 0).asSInt),
    srlw -> ((Cat(0.U((xlen - 32).W), a(31, 0)) >> b(4, 0)).asSInt),
    sraw -> ((Cat(Fill(xlen - 32, a(31)), a(31, 0)) >> b(4, 0)).asSInt),
    divw -> (divTop.io.output.bits.quotient(31, 0).asSInt),
    remw -> (divTop.io.output.bits.remainder(31, 0).asSInt),
    duw  -> (divTop.io.output.bits.quotient(31, 0).asSInt),
    ruw  -> (divTop.io.output.bits.remainder(31, 0).asSInt)) else Nil
  )
  result := Mux1H(operates.map(x => (io.input.bits.op === x._1, x._2)))

  when(io.input.bits.word) { io.output.bits := (Fill(32, result(31)) ## result(31, 0)).asSInt }
}

object Operators {
  var operators = Enum(32)
  val nop::add::sub::and::or::xor::sll::sra::Nil = operators.take(8)
  operators = operators.drop(8)
  val srl::lts::ltu::equ::neq::sllw::srlw::sraw::Nil = operators.take(8)
  operators = operators.drop(8)
  val ges::geu::mul::divw::remw::rem::div::remu::Nil = operators.take(8)
  operators = operators.drop(8)
  val divu::mulh::duw::ruw::max::min::maxu::minu::Nil = operators
  val quantity = 5
  val (lr, sc) = (sll, sra)
}

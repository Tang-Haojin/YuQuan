package cpu.component

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import cpu._
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
  io.output.valid := io.input.fire

  when(mulTop.io.input.fire || divTop.io.input.fire) { io.output.valid := 0.B }
  when(!mulTop.io.input.ready) { io.output.valid := mulTop.io.output.valid }
  when(!divTop.io.input.ready) { io.output.valid := divTop.io.output.valid }

  private val (cpop_hi, cpop_lo) = (if (xlen == 32) 0.U else PopCount(a(xlen - 1, 32)), PopCount(a(31, 0)))
  private val cpop_hi_lo = cpop_hi +& cpop_lo
  private val cpop_ans = Mux(io.input.bits.word || (xlen == 32).B, cpop_lo, cpop_hi_lo)

  private val shiftness = if (xlen != 32) Mux(io.input.bits.word, b(4, 0), b(5, 0)) else b(4, 0)
  private val sl = a.asUInt << shiftness
  private val (lessthan, ulessthan) = (a < b, a.asUInt < b.asUInt)

  private def replicating(num: Int, str: String)(originalString: String = str): String = num match {
    case 0 => ""
    case 1 => str
    case _ => replicating(num - 1, str + originalString)(originalString)
  }
  private val ctzMapping = (0 to xlen).map(i =>
    BitPat("b" + replicating(xlen - i, "?")() + replicating(i, "0")()) -> i.U(log2Ceil(xlen + 1).W)
  ).reverse
  private val ctz_ans = Lookup(a.asUInt, 0.U(log2Ceil(xlen + 1).W), ctzMapping)

  private val operates = Seq(
    nop  -> a,
    add  -> (a + b),
    sub  -> (a - b),
    and  -> (a & b),
    or   -> (a | b),
    xor  -> (a ^ b)) ++ (if (isLxb) Seq(
    nor  -> ~(a | b)) else Nil) ++ Seq(
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
    ruw  -> (divTop.io.output.bits.remainder(31, 0).asSInt)) else Nil) ++ (if (ext('B')) Seq(
    cpop -> (0.U((xlen - cpop_ans.getWidth).W) ## cpop_ans).asSInt,
    ctz  -> (0.U((xlen - ctz_ans.getWidth).W) ## ctz_ans).asSInt) else Nil
  )
  result := Mux1H(operates.map(x => (io.input.bits.op === x._1, x._2)))

  when(io.input.bits.word) { io.output.bits := (Fill(32, result(31)) ## result(31, 0)).asSInt }
}

object Operators {
  val quantity = 31
  val nop::add::sub::and::or::xor::nor::sll::Nil = Seq.tabulate(8)(x => (1 << x).U(quantity.W))
  val sra::srl::lts::ltu::sllw::srlw::sraw::mul::Nil = Seq.tabulate(8)(x => (1 << (x + 8)).U(quantity.W))
  val remw::rem::div::remu::divu::mulh::duw::ruw::Nil = Seq.tabulate(8)(x => (1 << (x + 16)).U(quantity.W))
  val divw::max::min::maxu::minu::cpop::ctz::Nil = Seq.tabulate(7)(x => (1 << (x + 24)).U(quantity.W))
  def lr = sll
  def sc(implicit p: Parameters) = if(p(GEN_NAME) == "lxb") nop else sra
  val muldivMask = (for { i <- 0 until quantity
    if (1 << i >= mul.litValue && 1 << i <= ruw.litValue)
  } yield 1 << i).fold(0)(_ | _).U(quantity.W)
}

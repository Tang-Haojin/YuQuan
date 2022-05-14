package cpu

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

import utils._
import peripheral._

class YQConfig extends Config(YQConfig.f)

object YQConfig {
  val f: (View, View, View) => PartialFunction[Any,Any] = (site, here, up) => {
    case XLEN             => site(GEN_NAME) match { case "lxb" => 32; case _ => 64 }
    case EXTENSIONS       => site(GEN_NAME) match { case "ysyx" => List('I', 'M', 'S', 'A', 'U', 'C'); case "zmb" => List('I', 'M'); case "lxb" => List('I', 'M', 'l') }
    case ALEN             => 32
    case IDLEN            => 4
    case USRLEN           => 0
    case USEQOS           => 0
    case USEPROT          => 0
    case USECACHE         => 0
    case USELOCK          => 0
    case USEREGION        => 0
    case ISAXI3           => site(GEN_NAME) match { case "lxb" => true; case _ => false }
    case AXIRENAME        => true
    case MODULE_PREFIX    => site(GEN_NAME) match { case "ysyx" => "ysyx_210153_"; case "zmb" => "zmb_"; case "lxb" => "lxb_" }
    case CLINT_MMAP       => new CLINT
    case SIMPLE_PLIC_MMAP => new SIMPLEPLIC
    case DRAM_MMAP        => new DRAM()
    case USEFLASH         => site(GEN_NAME) match { case "ysyx" => true; case _ => false }
    case SPIFLASH_MMAP    => new PeripheralConfig.SPIFLASH
    case ENABLE_DEBUG     => false
    case REG_CONF         => site(GEN_NAME) match { case "lxb" => new RegConf(3, 10, 5); case _ => new RegConf(3, 10, 4) }
    case TLB_ENTRIES      => 16
    case VALEN            => site(GEN_NAME) match { case "ysyx" => 64; case _ => 32 }
    case USESLAVE         => site(GEN_NAME) match { case "ysyx" => true; case _ => false }
    case USEPLIC          => site(GEN_NAME) match { case "ysyx" => true; case _ => false }
    case USECLINT         => site(GEN_NAME) match { case "ysyx" => true; case _ => false }
    case HANDLEMISALIGN   => site(GEN_NAME) match { case "ysyx" => true; case "zmb" => false; case "lxb" => true }
    case USEXILINX        => site(GEN_NAME) match { case "ysyx" => false; case "zmb" => true; case "lxb" => true }
    case USEPUBRAM        => site(GEN_NAME) match { case "ysyx" => false; case _ => false }
    case USEDIFFTEST      => site(GEN_NAME) match { case "lxb" => true; case _ => false }
  }

  class CLINT extends MMAP {
    override val BASE = 0x02000000L
    override val SIZE = 0x10000L
    val MSIP = (hartid: Int) => BASE + 4 * hartid
    val MTIMECMP = (hartid: Int) => BASE + 0x4000 + 8 * hartid
    val MTIME = BASE + 0xBFF8
  }

  class SIMPLEPLIC extends MMAP {
    override val BASE = 0x0c000000L
    override val SIZE = 0x04000000L
    val Priority = (source: Int) => BASE + 4 * source // Interrupt source n priority
    val Pending = (source: Int) => BASE + 0x1000 + 4 * (source / 32) // Interrupt Pending bit
    val Enable = (source: Int, context: Int) => BASE + 0x2000 + 0x80 * context + 4 * (source / 32) // Interrupt Enable Bits
    val Threshold = (context: Int) => BASE + 0x200000 + 0x1000 * context // threshold
    val CLAIM  = (context: Int) => Threshold(context) + 4 // claim & complete
  }

  class DRAM(
    override val BASE: Long = 0x80000000L,
    override val SIZE: Long = 0x80000000L
  ) extends MMAP

  class RegConf(val readPortsNum: Int, val readCsrsPort: Int, val writeCsrsPort: Int)

  def apply(): YQConfig = new YQConfig
}

case object GEN_NAME         extends Field[String]
case object EXTENSIONS       extends Field[List[Char]]
case object CLINT_MMAP       extends Field[YQConfig.CLINT]
case object SIMPLE_PLIC_MMAP extends Field[YQConfig.SIMPLEPLIC]
case object DRAM_MMAP        extends Field[YQConfig.DRAM]
case object USEFLASH         extends Field[Boolean]
case object ENABLE_DEBUG     extends Field[Boolean]
case object REG_CONF         extends Field[YQConfig.RegConf]
case object TLB_ENTRIES      extends Field[Int]
case object VALEN            extends Field[Int]
case object USESLAVE         extends Field[Boolean]
case object USEPLIC          extends Field[Boolean]
case object USECLINT         extends Field[Boolean]
case object HANDLEMISALIGN   extends Field[Boolean]
case object USEDIFFTEST      extends Field[Boolean]

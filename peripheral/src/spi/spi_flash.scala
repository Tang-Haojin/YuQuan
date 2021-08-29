package peripheral.spi

import chisel3._
import chisel3.util._
import chisel3.experimental.Param
import chipsalliance.rocketchip.config._

import utils._

class SpiFlashIO(implicit val p: Parameters) extends Bundle with ApbSlaveIOTrait with SpiMasterIOTrait

class spi_flash(params: Map[String, Param] = Map.empty[String, Param])(implicit p: Parameters) extends BlackBox(params) {
  val io = IO(new SpiFlashIO)
}

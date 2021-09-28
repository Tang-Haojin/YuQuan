package utils.zmb

import chisel3._

class LogCtrl extends Bundle {
  val log = new Bundle {
    val begin = UInt(64.W)
    val end   = UInt(64.W)
    val level = UInt(64.W)
  }
}

class PerfInfo extends Bundle {
  val clean = Bool()
  val dump  = Bool()
}

class Uart extends Bundle {
  val out = Output(new Bundle {
    val valid = Bool()
    val ch    = UInt(8.W)
  })
  val in = new Bundle {
    val valid = Output(Bool())
    val ch    = Input (UInt(8.W))
  }
}

package sim

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.config.GeneralConfig._

class AxiSlaveIO extends Bundle {
  val basic = new BASIC
  val axiWa = Flipped(new AXIwa)
  val axiWd = Flipped(new AXIwd)
  val axiWr = Flipped(new AXIwr)
  val axiRa = Flipped(new AXIra)
  val axiRd = Flipped(new AXIrd)
}

class ROUTER extends RawModule {
  val io = IO(new AxiSlaveIO)

  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(1.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
                                io.axiRa.ARREADY := 1.B
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RID    = RegInit(0.U(4.W)); io.axiRd.RID := RID
    val WDATA  = RegInit(0.U(XLEN.W))
    
    io.axiRd.RDATA := (-1.S).asUInt

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      RID := io.axiRa.ARID
      RVALID := 1.B
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWREADY := 0.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      WDATA  := io.axiWd.WDATA
      WREADY := 0.B
    }

    when(~io.axiWa.AWREADY && ~io.axiWd.WREADY) {
      AWREADY := 1.B
      WREADY  := 1.B
      printf("%c", WDATA(7, 0))
      BVALID  := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }
  }
}

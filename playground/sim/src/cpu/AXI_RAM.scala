package sim

import chisel3._
import chisel3.util._

import cpu.axi._
import cpu.config.GeneralConfig._
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.util.experimental.loadMemoryFromFileInline

class RAM extends RawModule {
  val io = IO(new Bundle {
    val basic = new BASIC
    val axiWa = Flipped(new AXIwa)
    val axiWd = Flipped(new AXIwd)
    val axiWr = Flipped(new AXIwr)
    val axiRa = Flipped(new AXIra)
    val axiRd = Flipped(new AXIrd)
  })

  // val vecDataIn  = Wire(Vec(XLEN / 8, UInt(8.W)))
  // val vecDataOut = Wire(Vec(XLEN / 8, UInt(8.W)))
  // val mask       = Wire(Vec(XLEN / 8, Bool()))

  io.axiWr.BID := 1.U // since only cpu requests writing now
  io.axiWr.BRESP := DontCare
  io.axiWr.BUSER := DontCare

  io.axiRd.RLAST := 1.B
  io.axiRd.RUSER := DontCare
  io.axiRd.RRESP := DontCare


  withClockAndReset(io.basic.ACLK, ~io.basic.ARESETn) {
    val syncRAM = SyncReadMem(1024, UInt(8.W))
    loadMemoryFromFileInline(syncRAM, "mem.txt")

    val AWREADY = RegInit(1.B); io.axiWa.AWREADY := AWREADY
    val WREADY  = RegInit(1.B); io.axiWd.WREADY  := WREADY
    val BVALID  = RegInit(0.B); io.axiWr.BVALID  := BVALID
    val ARREADY = RegInit(1.B); io.axiRa.ARREADY := ARREADY
    val RVALID  = RegInit(0.B); io.axiRd.RVALID  := RVALID

    val RpreValid = RegInit(0.B);

    val RID    = RegInit(0.U(4.W)); io.axiRd.RID := RID
    val ARADDR = RegInit(0.U(XLEN.W))
    val AWADDR = RegInit(0.U(XLEN.W))
    val WDATA  = RegInit(0.U(XLEN.W))
    
    io.axiRd.RDATA := 
      Cat((for { a <- 0 until XLEN / 8 } yield syncRAM.read(ARADDR + a.U)).reverse)

    // printf("slave: io.axiRd.RDATA = %x\n", io.axiRd.RDATA)

    when(io.axiRd.RVALID && io.axiRd.RREADY) {
      RVALID  := 0.B
      ARREADY := 1.B
    }.elsewhen(RpreValid) {
      RpreValid := 0.B
      RVALID    := 1.B
    }.elsewhen(io.axiRa.ARVALID && io.axiRa.ARREADY) {
      RID := io.axiRa.ARID
      ARADDR := io.axiRa.ARADDR
      ARREADY := 0.B
      RpreValid := 1.B
    }

    when(io.axiWa.AWVALID && io.axiWa.AWREADY) {
      AWADDR  := io.axiWa.AWADDR
      AWREADY := 0.B
    }

    when(io.axiWd.WVALID && io.axiWd.WREADY) {
      WDATA  := io.axiWd.WDATA
      WREADY := 0.B
    }

    when(~io.axiWa.AWREADY && ~io.axiWd.WREADY) {
      AWREADY := 1.B
      WREADY  := 1.B
      syncRAM.write(AWADDR, WDATA)
      BVALID  := 1.B
    }

    when(io.axiWr.BVALID && io.axiWr.BREADY) {
      BVALID := 0.B
    }
  }
}
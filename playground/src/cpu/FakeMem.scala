package cpu

import chisel3._
import chisel3.util._

import cpu.axi._

import cpu.register._
import cpu.config.GeneralConfig._
import cpu.config.RegisterConfig._
import cpu.config.Debug._
import ExecSpecials._

class FakeMem extends Module {
  val io = IO(new Bundle {
    val addr_in  = Input(UInt(XLEN.W))
    val addr_out = Output(UInt(12.W))

    val data_in  = Input(UInt(XLEN.W))
    val data_out = Output(UInt(XLEN.W))

    val special  = Input(UInt(4.W))
    val mask     = Input(UInt(3.W))

    val fake_in  = Flipped(new cpu.privileged.FakeMemOut)
  })

  val wireData = WireDefault(0.U(XLEN.W))

  io.addr_out := 0xFFF.U
  io.data_out := 0.U

  class CSRsAddr extends cpu.privileged.CSRsAddr
  val csrsAddr = new CSRsAddr

  when(io.special === ld) {
    switch(io.addr_in) {
      is(CLINT.MTIME.U) {
        io.addr_out := csrsAddr.Mtime
        wireData := io.fake_in.mtime
      }
      is(CLINT.MTIMECMP(0).U) {
        io.addr_out := csrsAddr.Mtimecmp
        wireData := io.fake_in.mtimecmp
      }
    }
    switch(io.mask) {
      is(0.U) { io.data_out := Cat(Fill(XLEN - 8 , wireData( 7)), wireData( 7, 0)) }
      is(1.U) { io.data_out := Cat(Fill(XLEN - 16, wireData(15)), wireData(15, 0)) }
      is(2.U) { io.data_out := Cat(Fill(XLEN - 32, wireData(31)), wireData(31, 0)) }
      is(3.U) { io.data_out :=                                    wireData         }
      is(4.U) { io.data_out := Cat(Fill(XLEN - 8 ,          0.B), wireData( 7, 0)) }
      is(5.U) { io.data_out := Cat(Fill(XLEN - 16,          0.B), wireData(15, 0)) }
      is(6.U) { io.data_out := Cat(Fill(XLEN - 32,          0.B), wireData(31, 0)) }
    }
  }.elsewhen(io.special === st) {
    switch(io.addr_in) {
      is(CLINT.MTIME.U) {
        io.addr_out := csrsAddr.Mtime
        wireData := io.fake_in.mtime
      }
      is(CLINT.MTIMECMP(0).U) {
        io.addr_out := csrsAddr.Mtimecmp
        wireData := io.fake_in.mtimecmp
      }
    }
    switch(io.mask) {
      is(0.U) { io.data_out := Cat(wireData(XLEN - 1,  8), io.data_in( 7, 0)) }
      is(1.U) { io.data_out := Cat(wireData(XLEN - 1, 16), io.data_in(15, 0)) }
      is(2.U) { io.data_out := Cat(wireData(XLEN - 1, 32), io.data_in(31, 0)) }
      is(3.U) { io.data_out :=     wireData                                   }
    }
  }
}

class IsCLINT extends Module {
  val io = IO(new Bundle {
    val addr_in  = Input(UInt(XLEN.W))
    val addr_out = Output(UInt(12.W))
  })

  class CSRsAddr extends cpu.privileged.CSRsAddr
  val csrsAddr = new CSRsAddr

  io.addr_out := 0xFFF.U

  switch(io.addr_in) {
    is(CLINT.MTIME.U) { io.addr_out := csrsAddr.Mtime }
    is(CLINT.MTIMECMP(0).U) { io.addr_out := csrsAddr.Mtimecmp }
  }

}
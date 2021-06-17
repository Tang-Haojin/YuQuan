package cpu.privileged

import chisel3._
import chisel3.util._
import cpu.config.{GeneralConfig => p}
import p._
import chisel3.util.log2Floor

class M_CSRs extends Module {
  val io = IO(new Bundle {
    
  })

  val MXL   = (log2Down(XLEN) - 4).U(2.W)
  val MXLEN = WireDefault(32.U)
  when (MXL === 2.U) { MXLEN := 64.U }

  val Mvendorid     = 0xF11.U
  val Marchid       = 0xF12.U
  val Mimpid        = 0xF13.U
  val Mhartid       = 0xF14.U

  val Mstatus       = 0x300.U
  val Misa          = 0x301.U
  val Medeleg       = 0x302.U
  val Mideleg       = 0x303.U
  val Mie           = 0x304.U
  val Mtvec         = 0x305.U
  val Mcounteren    = 0x306.U

  val Mscratch      = 0x340.U
  val Mpec          = 0x341.U
  val Mcause        = 0x342.U
  val Mtval         = 0x343.U
  val Mip           = 0x344.U

  val Pmpcfg0       = 0x3A0.U
  val Pmpcfg1       = if (XLEN == 32) 0x3A1.U else null
  val Pmpcfg2       = 0x3A2.U
  val Pmpcfg3       = if (XLEN == 32) 0x3A3.U else null
  val Pmpaddr       = (n: UInt) => 0x3B0.U + n

  val Mcycle        = 0xB00.U
  val Minstret      = 0xB02.U
  val Mhpmcounter   = (n: UInt) => 0xB00.U + n
  val Mcycleh       = if (XLEN == 32) 0xB80.U else null
  val Minstreth     = if (XLEN == 32) 0xB82.U else null
  val Mhpmcounterh  = if (XLEN == 32) (n: UInt) => 0xB80.U + n else null

  val Mcountinhibit = 0x320.U
  val Mhpmevent     = (n: UInt) => 0x320.U + n


  val Extensions = p.Extensions.foldLeft(0)((res, x) => res | 1 << x - 'A').U

  val misa = RegInit(UInt(XLEN.W), MXL << MXLEN | Extensions)
  val mvendorid = RegInit(0.U(32.W)) // non-commercial implementation
  val marchid = RegInit(0.U(XLEN.W)) // the field is not implemented
  val mimpid = RegInit(0.U(XLEN.W)) // the field is not implemented
  val mhartid = RegInit(0.U(XLEN.W)) // the hart that running the code
  val mstatus = MstatusInit(0.U(XLEN.W)) // the hart that running the code

}

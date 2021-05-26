package cpu

import chisel3._
import chisel3.util._

// instruction decoding module
class ID extends Module {
  val io = IO(new Bundle {
    // Global signal
    val in       = Input (UInt( 1.W))
    val ARESETn  = Input (UInt( 1.W))
    // internal handshaking
    val ready = Output(UInt(1.W))
    val valid = Input(UInt(1.W))
    val ready_next = Input(UInt(1.W))
    val valid_next = Output(UInt(1.W))
    // instruction
    val instruction = Input(UInt(32.W))
    val instype = Output(UInt(3.W))
  })

  val instruction = Reg(UInt(32.W))
  val valid = Reg(UInt(1.W))
  val ready_next = Reg(UInt(1.W))

  instruction := io.instruction
  valid := io.valid
  ready_next := io.ready_next
  
  // decode
  when(instruction(1, 0) === 0x3.U) {
    io.valid_next := 0.U // invalid instruction
  }.otherwise {
    switch(instruction(6, 2)) {
      is(0x00.U) { // IDEX (0b00000, I, load)
        ???
      }
      is(0x04.U) { // IDEX (0b00100, I, computei)
        ???
      }
      is(0x05.U) { // IDEX (0b00101, U, auipc)
        ???
      }
      is(0x06.U) { // IDEX (0b00110, I, computeiw)
        ???
      }
      is(0x08.U) { // IDEX (0b01000, S, store)
        ???
      }
      is(0x0c.U) { // IDEX (0b01100, R, compute)
        ???
      }
      is(0x0d.U) { // IDEX (0b01101, U, lui)
        ???
      }
      is(0x0e.U) { // IDEX (0b01110, R, computew)
        ???
      }
      is(0x18.U) { // IDEX (0b11000, B, branch)
        ???
      }
      is(0x19.U) { // IDEX (0b11001, I, jalr)
        ???
      }
      is(0x1b.U) { // IDEX (0b11011, J, jal)
        ???
      }
      is(0x1c.U) { // IDEX (0b11100, I, raise)
        ???
      }
    }
  }
}
package cpu

import chisel3._
import chisel3.util._

// instruction decoding module
class ID extends Module {
  val io = IO(new Bundle {
    // Global signal
    val in          = Input (UInt( 1.W))
    val ARESETn     = Input (UInt( 1.W))
    // internal handshaking
    val ready       = Output(UInt( 1.W))
    val valid       = Input (UInt( 1.W))
    val ready_next  = Input (UInt( 1.W))
    val valid_next  = Output(UInt( 1.W))
    // instruction
    val instruction = Input (UInt(32.W))
    // decoded
    val imm         = Output(UInt(32.W))
    val rs1         = Output(UInt( 5.W))
    val rs2         = Output(UInt( 5.W))
    val rd          = Output(UInt( 5.W))
    val opcode      = Output(UInt( 7.W))
    val funct3      = Output(UInt( 3.W))
    val funct7      = Output(UInt( 7.W))
  })

  val i :: u :: s :: r :: j :: b :: Nil = Enum(6)

  val instruction = Reg(UInt(32.W))
  val valid       = Reg(UInt( 1.W))
  val ready_next  = Reg(UInt( 1.W))
  val instype     = Reg(UInt( 3.W))

  instruction := io.instruction
  valid := io.valid
  ready_next := io.ready_next

  io.opcode := instruction( 6,  0)
  io.funct3 := instruction(14, 12)
  io.funct7 := instruction(31, 25)
  io.rs1    := instruction(19, 15)
  io.rs2    := instruction(24, 20)
  io.rd     := instruction(11,  7)
  
  // decode
  when(instruction(1, 0) === 0x3.U) {
    io.valid_next := 0.U // invalid instruction
  }.otherwise {
    switch(instruction(6, 2)) {
      is(0x00.U) { // IDEX (0b00000, I, load)
        instype := i
        ???
      }
      is(0x04.U) { // IDEX (0b00100, I, computei)
        instype := i
        ???
      }
      is(0x05.U) { // IDEX (0b00101, U, auipc)
        instype := u
        ???
      }
      is(0x06.U) { // IDEX (0b00110, I, computeiw)
        instype := i
        ???
      }
      is(0x08.U) { // IDEX (0b01000, S, store)
        instype := s
        ???
      }
      is(0x0c.U) { // IDEX (0b01100, R, compute)
        instype := r
        ???
      }
      is(0x0d.U) { // IDEX (0b01101, U, lui)
        instype := u
        ???
      }
      is(0x0e.U) { // IDEX (0b01110, R, computew)
        instype := r
        ???
      }
      is(0x18.U) { // IDEX (0b11000, B, branch)
        instype := b
        ???
      }
      is(0x19.U) { // IDEX (0b11001, I, jalr)
        instype := i
        ???
      }
      is(0x1b.U) { // IDEX (0b11011, J, jal)
        instype := j
        ???
      }
      is(0x1c.U) { // IDEX (0b11100, I, raise)
        instype := i
        ???
      }
    }
  }
}
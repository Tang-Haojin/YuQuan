package sim

import chisel3._
import chisel3.util._

class ScanRead extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val getch = Input (Bool())
    val empty = Output(Bool())
    val data  = Output(UInt(8.W))
  })

  setInline("ScanRead.v", s"""
    |import "DPI-C" function void scan_read(output bit empty, output byte data);
    |
    |module ScanRead (
    |  input  clock,
    |  input  getch,
    |  output reg       empty,
    |  output reg [7:0] data
    |);
    |
    |  always@(posedge clock) begin
    |    if (getch) scan_read(empty, data);
    |  end
    |
    |endmodule
  """.stripMargin)
}

class TTY extends RawModule {
  val io = IO(new Bundle {
    val clock = Input (Clock())
    val reset = Input (Bool())
	  val stx   = Output(UInt(1.W))
    val srx   = Input (UInt(1.W))
  })

  val BAUD_RATE = 115200
  val INTERVAL = 1000000000 / BAUD_RATE
  val WAIT_CYCLE = INTERVAL / 2
  val idle::starting::running::ending::Nil = Enum(4)

  io.stx := 1.U

  withClockAndReset(io.clock, ~io.reset) {
    val readState = RegInit(0.U(2.W))
    val readWaitCounter = RegInit(0.U(14.W))
    val readBitIndex = RegInit(0.U(3.W))
    val readData = RegInit(0.U(8.W))

    when(readState === idle) {
      when(io.srx === 0.U) { readState := starting }
    }

    when(readState === starting) {
      when(readWaitCounter <= (WAIT_CYCLE / 2).U) {
        readWaitCounter := readWaitCounter + 1.U;
      }.otherwise {
        readWaitCounter := 0.U
        readState := running
        readBitIndex := 0.U
      }
    }

    when(readState === running) {
      when(readWaitCounter <= WAIT_CYCLE.U) {
        readWaitCounter := readWaitCounter + 1.U
      }.otherwise {
        readWaitCounter := 0.U
        readBitIndex := readBitIndex + 1.U
        readData := io.srx ## readData(7, 1)
        when(readBitIndex === 7.U) { readState := ending }
      }
    }

    when(readState === ending) {
      when(readWaitCounter <= WAIT_CYCLE.U) {
        readWaitCounter := readWaitCounter + 1.U
      }.otherwise {
        readWaitCounter := 0.U
        readState := idle
        printf("%c", readData)
      }
    }

    val writeState = RegInit(0.U(2.W))
    val writeWaitCounter = RegInit(0.U(14.W))
    val writeBitIndex = RegInit(0.U(3.W))
    val writeData = RegInit(0.U(8.W))

    val scanRead = Module(new ScanRead)
    scanRead.io.clock := io.clock
    scanRead.io.getch := 0.B

    when(writeState === idle) {
      scanRead.io.getch := 1.B
      writeData := scanRead.io.data
      when(!scanRead.io.empty) { writeState := starting }
    }

    when(writeState === starting) {
      io.stx := 0.U
      when(writeWaitCounter <= WAIT_CYCLE.U) {
        writeWaitCounter := writeWaitCounter + 1.U
      }.otherwise {
        writeWaitCounter := 0.U
        writeState := running
      }
    }

    when(writeState === running) {
      io.stx := writeData(0).asUInt
      when(writeWaitCounter <= WAIT_CYCLE.U) {
        writeWaitCounter := writeWaitCounter + 1.U
      }.otherwise {
        writeWaitCounter := 0.U
        writeBitIndex := writeBitIndex + 1.U
        writeData := (writeData >> 1)
        when(writeBitIndex === 7.U) {
          writeState := ending
        }
      }
    }

    when(writeState === ending) {
      when(writeWaitCounter <= WAIT_CYCLE.U) {
        writeWaitCounter := writeWaitCounter + 1.U
      }.otherwise {
        writeState := idle
        writeWaitCounter := 0.U
      }
    }
  }
}

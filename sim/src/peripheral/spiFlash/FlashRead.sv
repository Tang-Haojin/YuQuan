`timescale 1ns / 10ps

import "DPI-C" function longint flash_read(input longint addr);

module FlashRead (
  input             clock,
  input             ren,
  input      [63:0] addr,
  output reg [63:0] data
);
  always@(posedge clock) begin
    if (ren) data <= flash_read(addr);
  end
endmodule

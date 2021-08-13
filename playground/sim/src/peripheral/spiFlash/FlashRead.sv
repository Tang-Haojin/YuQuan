`timescale 1ns / 10ps

import "DPI-C" function void flash_read(input longint addr, output longint data);

module FlashRead (
  input             clock,
  input             ren,
  input      [63:0] addr,
  output reg [63:0] data
);
  always@(posedge clock) begin
    if (ren) flash_read(addr, data);
  end
endmodule

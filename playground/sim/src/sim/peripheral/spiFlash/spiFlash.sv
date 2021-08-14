`timescale 1ns / 10ps

`define spi_cs_num 2

module spiFlash (
  input                    spi_clk,
  input  [`spi_cs_num-1:0] spi_cs,
  input                    spi_mosi,
  output wire              spi_miso,
  input                    spi_irq_out
);

  typedef enum [2:0] { cmd_t, addr_t, data_t, err_t } state_t;

  wire reset; assign reset = spi_cs[0];

  reg [2:0]  state;
  reg [7:0]  counter;
  reg [7:0]  cmd;
  reg [21:0] addr;
  reg [63:0] data;   assign spi_miso = data[63];

  wire        ren;   assign ren = (state == addr_t) && (counter == 8'd22);
  wire [63:0] rdata;
  wire [63:0] raddr; assign raddr = { 40'd0, addr, 2'd0 };
  FlashRead flashRead (
    .clock(spi_clk),
    .ren(ren),
    .addr(raddr),
    .data(rdata)
  );

  always@(posedge spi_clk or posedge reset) begin
    if (reset) state <= cmd_t;
    else begin
      case (state)
        cmd_t:  state <= (counter == 8'd7 ) ? addr_t : state;
        addr_t: state <= (cmd     != 8'h3 ) ? err_t  :
                         (counter == 8'd23) ? data_t : state;
        data_t: state <= state;
        
        default: begin
          state <= state;
          $fwrite(32'h80000002, "Assertion failed: only support `03h` read command\n");
          $fatal;
        end
      endcase
    end
  end

  always@(posedge spi_clk or posedge reset) begin
    if (reset) counter <= 8'd0;
    else begin
      case (state)
        cmd_t:   counter <= (counter < 8'd7 ) ? counter + 8'd1 : 8'd0;
        addr_t:  counter <= (counter < 8'd23) ? counter + 8'd1 : 8'd0;
        default: counter <= counter + 8'd1;
      endcase
    end
  end

  always@(posedge spi_clk or posedge reset) begin
    if (reset)               cmd <= 8'd0;
    else if (state == cmd_t) cmd <= { cmd[6:0], spi_mosi };
  end

  always@(posedge spi_clk or posedge reset) begin
    if (reset) addr <= 22'd0;
    else if (state == addr_t && counter < 8'd22)
      addr <= { addr[20:0], spi_mosi };
  end

  always@(posedge spi_clk or posedge reset) begin
    if (reset) data <= 64'd0;
    else if (state == addr_t && counter == 8'd23)
      data <= {
        rdata[ 7: 0], rdata[15: 8], rdata[23:16], rdata[31:24],
        rdata[39:32], rdata[47:40], rdata[55:48], rdata[63:56]
      };
    else if (state == data_t) data <= { data[62:0], data[63] };
  end

endmodule

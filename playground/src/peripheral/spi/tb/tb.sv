`timescale 1ns / 10ps

module tb(clock, rst_n);
  input clock;
  input rst_n;

  wire [3:0]  AWID;    assign AWID    = 4'd1;
  wire [31:0] AWADDR;  assign AWADDR  = 32'h40002000;
  wire [3:0]  AWLEN;   assign AWLEN   = 4'd0;
  wire [1:0]  AWSIZE;  assign AWSIZE  = 2'd2;
  wire        AWVALID; assign AWVALID = 1'b0;
  wire        AWREADY;

  wire [3:0]  WID;     assign WID     = 4'd1;
  wire [31:0] WDATA;   assign WDATA   = 32'd3;
  wire [3:0]  WSTRB;   assign WSTRB   = 4'h0;
  wire        WLAST;   assign WLAST   = 1'b1;
  wire        WVALID;  assign WVALID  = 1'b0;
  wire        WREADY;

  wire [3:0]  BID;
  wire [1:0]  BRESP;
  wire        BVALID;
  wire        BREADY;  assign BREADY  = 1'b1;

  wire [3:0]  ARID;    assign ARID    = 4'd2;
  wire [31:0] ARADDR;  assign ARADDR  = 32'h40001000;
  wire [3:0]  ARLEN;   assign ARLEN   = 4'd0;
  wire [1:0]  ARSIZE;  assign ARSIZE  = 2'd2;
  reg         ARVALID;
  wire        ARREADY;

  wire [3:0]  RID;
  wire [31:0] RDATA;
  wire [1:0]  RRESP;
  wire        RLAST;
  wire        RVALID;
  wire        RREADY;  assign RREADY  = 1'b1;

  wire        spi_clk;
  wire [1:0]  spi_cs;
  wire        spi_mosi;
  wire        spi_miso;
  wire        spi_irq_out;

  reg [31:0] counter;

  always@(posedge clock) begin
    if (!rst_n) counter <= 32'd0;
    else begin
      if (counter < 64) counter <= counter + 32'd1;
    end
  end

  always@(posedge clock) begin
    if (!rst_n) ARVALID <= 1'b0;
    else begin
      if (counter == 32)      ARVALID <= 1'b1;
      if (ARREADY && ARVALID) ARVALID <= 1'b0;
    end
  end

  spiFlash spiFlash (
    .spi_clk(spi_clk),
    .spi_cs(spi_cs),
    .spi_mosi(spi_mosi),
    .spi_miso(spi_miso),
    .spi_irq_out(spi_irq_out)
  );

  spi_axi_flash spi_axi_flash_0 (
    .ACLK(clock),
    .ARESETn(rst_n),
    .AWID(AWID),
    .AWADDR(AWADDR),
    .AWLEN(AWLEN),
    .AWSIZE(AWSIZE),
    .AWVALID(AWVALID),
    .AWREADY(AWREADY),
    .WID(WID),
    .WDATA(WDATA),
    .WSTRB(WSTRB),
    .WLAST(WLAST),
    .WVALID(WVALID),
    .WREADY(WREADY),
    .BID(BID),
    .BRESP(BRESP),
    .BVALID(BVALID),
    .BREADY(BREADY),
    .ARID(ARID),
    .ARADDR(ARADDR),
    .ARLEN(ARLEN),
    .ARSIZE(ARSIZE),
    .ARVALID(ARVALID),
    .ARREADY(ARREADY),
    .RID(RID),
    .RDATA(RDATA),
    .RRESP(RRESP),
    .RLAST(RLAST),
    .RVALID(RVALID),
    .RREADY(RREADY),

    .spi_clk(spi_clk),
    .spi_cs(spi_cs),
    .spi_mosi(spi_mosi),
    .spi_miso(spi_miso),
    .spi_irq_out(spi_irq_out)
  );

endmodule

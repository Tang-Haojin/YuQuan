`include "amba_define.v"

`define flash_addr_start 32'h40000000
`define flash_addr_end   32'h47ffffff
`define spi_cs_num       2

module spi_axi_flash
(
  input                      ACLK,
  input                      ARESETn,

  input [3:0]                AWID,
  input [`A_ADDR_W-1:0]      AWADDR,
  input [3:0]                AWLEN,
  input [1:0]                AWSIZE,
  input                      AWVALID,
  output                     AWREADY,
  input [3:0]                WID,
  input [`A_DATA_W-1:0]      WDATA,
  input [`A_STRB_W-1:0]      WSTRB,
  input                      WLAST,
  input                      WVALID,
  output                     WREADY,
  output [3:0]               BID,
  output [1:0]               BRESP,
  output                     BVALID,
  input                      BREADY,
  input [3:0]                ARID,
  input [`A_ADDR_W-1:0]      ARADDR,
  input [3:0]                ARLEN,
  input [1:0]                ARSIZE,
  input                      ARVALID,
  output                     ARREADY,
  output [3:0]               RID,
  output [`A_DATA_W-1:0]     RDATA,
  output [1:0]               RRESP,
  output                     RLAST,
  output                     RVALID,
  input                      RREADY,

  output                     spi_clk,
  output  [`spi_cs_num-1:0]  spi_cs,
  output                     spi_mosi,
  input                      spi_miso,
  output                     spi_irq_out
);

  wire                   pclk;
  wire                   presetn;
  wire                   psel;
  wire                   penable;
  wire                   pwrite;
  wire [31:0]            paddr;
  wire [31:0]            pwdata;
  wire [3:0]             pwstrb;
  wire [31:0]            prdata;
  wire                   pslverr;
  wire                   pready;

  spi_flash #(
    .flash_addr_start(`flash_addr_start),
    .flash_addr_end(`flash_addr_end),
    .spi_cs_num(`spi_cs_num)
  ) spi_flash_0 (
    .pclk(pclk),
    .presetn(presetn),
    .psel(psel),
    .penable(penable),
    .pwrite(pwrite),
    .paddr(paddr),
    .pwdata(pwdata),
    .pwstrb(pwstrb),
    .prdata(prdata),
    .pslverr(pslverr),
    .pready(pready),

    .spi_clk(spi_clk),
    .spi_cs(spi_cs),
    .spi_mosi(spi_mosi),
    .spi_miso(spi_miso),
    .spi_irq_out(spi_irq_out)
  );

  axi2apb axi2apb_0 (
    .ACLK(ACLK),
    .ARESETn(ARESETn),
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
    
    .pclk(pclk),
    .presetn(presetn),
    .psel(psel),
    .penable(penable),
    .pwrite(pwrite),
    .paddr(paddr),
    .pwdata(pwdata),
    .pwstrb(pwstrb),
    .prdata(prdata),
    .pslverr(pslverr),
    .pready(pready)
  );

endmodule

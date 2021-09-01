module SimTop(
  input clock,
  input reset
);

wire        cpu_reset;
wire        cpu_intr;
wire        cpu_master_0_awready;
wire        cpu_master_0_awvalid;
wire [3:0]  cpu_master_0_awid;
wire [31:0] cpu_master_0_awaddr;
wire [7:0]  cpu_master_0_awlen;
wire [2:0]  cpu_master_0_awsize;
wire [1:0]  cpu_master_0_awburst;
wire        cpu_master_0_wready;
wire        cpu_master_0_wvalid;
wire [63:0] cpu_master_0_wdata;
wire [7:0]  cpu_master_0_wstrb;
wire        cpu_master_0_wlast;
wire        cpu_master_0_bready;
wire        cpu_master_0_bvalid;
wire [3:0]  cpu_master_0_bid;
wire [1:0]  cpu_master_0_bresp;
wire        cpu_master_0_arready;
wire        cpu_master_0_arvalid;
wire [3:0]  cpu_master_0_arid;
wire [31:0] cpu_master_0_araddr;
wire [7:0]  cpu_master_0_arlen;
wire [2:0]  cpu_master_0_arsize;
wire [1:0]  cpu_master_0_arburst;
wire        cpu_master_0_rready;
wire        cpu_master_0_rvalid;
wire [3:0]  cpu_master_0_rid;
wire [63:0] cpu_master_0_rdata;
wire [1:0]  cpu_master_0_rresp;
wire        cpu_master_0_rlast;
wire        cpu_slave_awready;
wire        cpu_slave_awvalid;
wire [3:0]  cpu_slave_awid;
wire [31:0] cpu_slave_awaddr;
wire [7:0]  cpu_slave_awlen;
wire [2:0]  cpu_slave_awsize;
wire [1:0]  cpu_slave_awburst;
wire        cpu_slave_wready;
wire        cpu_slave_wvalid;
wire [63:0] cpu_slave_wdata;
wire [7:0]  cpu_slave_wstrb;
wire        cpu_slave_wlast;
wire        cpu_slave_bready;
wire        cpu_slave_bvalid;
wire [3:0]  cpu_slave_bid;
wire [1:0]  cpu_slave_bresp;
wire        cpu_slave_arready;
wire        cpu_slave_arvalid;
wire [3:0]  cpu_slave_arid;
wire [31:0] cpu_slave_araddr;
wire [7:0]  cpu_slave_arlen;
wire [2:0]  cpu_slave_arsize;
wire [1:0]  cpu_slave_arburst;
wire        cpu_slave_rready;
wire        cpu_slave_rvalid;
wire [3:0]  cpu_slave_rid;
wire [63:0] cpu_slave_rdata;
wire [1:0]  cpu_slave_rresp;
wire        cpu_slave_rlast;
wire        uart_rx;
wire        uart_tx;

ysyxSoCFull soc(
  .clock(clock),
  .reset(reset),
  .cpu_reset(cpu_reset),
  .cpu_intr(cpu_intr),
  .cpu_master_0_awready(cpu_master_0_awready),
  .cpu_master_0_awvalid(cpu_master_0_awvalid),
  .cpu_master_0_awid(cpu_master_0_awid),
  .cpu_master_0_awaddr(cpu_master_0_awaddr),
  .cpu_master_0_awlen(cpu_master_0_awlen),
  .cpu_master_0_awsize(cpu_master_0_awsize),
  .cpu_master_0_awburst(cpu_master_0_awburst),
  .cpu_master_0_wready(cpu_master_0_wready),
  .cpu_master_0_wvalid(cpu_master_0_wvalid),
  .cpu_master_0_wdata(cpu_master_0_wdata),
  .cpu_master_0_wstrb(cpu_master_0_wstrb),
  .cpu_master_0_wlast(cpu_master_0_wlast),
  .cpu_master_0_bready(cpu_master_0_bready),
  .cpu_master_0_bvalid(cpu_master_0_bvalid),
  .cpu_master_0_bid(cpu_master_0_bid),
  .cpu_master_0_bresp(cpu_master_0_bresp),
  .cpu_master_0_arready(cpu_master_0_arready),
  .cpu_master_0_arvalid(cpu_master_0_arvalid),
  .cpu_master_0_arid(cpu_master_0_arid),
  .cpu_master_0_araddr(cpu_master_0_araddr),
  .cpu_master_0_arlen(cpu_master_0_arlen),
  .cpu_master_0_arsize(cpu_master_0_arsize),
  .cpu_master_0_arburst(cpu_master_0_arburst),
  .cpu_master_0_rready(cpu_master_0_rready),
  .cpu_master_0_rvalid(cpu_master_0_rvalid),
  .cpu_master_0_rid(cpu_master_0_rid),
  .cpu_master_0_rdata(cpu_master_0_rdata),
  .cpu_master_0_rresp(cpu_master_0_rresp),
  .cpu_master_0_rlast(cpu_master_0_rlast),
  .cpu_slave_awready(cpu_slave_awready),
  .cpu_slave_awvalid(cpu_slave_awvalid),
  .cpu_slave_awid(cpu_slave_awid),
  .cpu_slave_awaddr(cpu_slave_awaddr),
  .cpu_slave_awlen(cpu_slave_awlen),
  .cpu_slave_awsize(cpu_slave_awsize),
  .cpu_slave_awburst(cpu_slave_awburst),
  .cpu_slave_wready(cpu_slave_wready),
  .cpu_slave_wvalid(cpu_slave_wvalid),
  .cpu_slave_wdata(cpu_slave_wdata),
  .cpu_slave_wstrb(cpu_slave_wstrb),
  .cpu_slave_wlast(cpu_slave_wlast),
  .cpu_slave_bready(cpu_slave_bready),
  .cpu_slave_bvalid(cpu_slave_bvalid),
  .cpu_slave_bid(cpu_slave_bid),
  .cpu_slave_bresp(cpu_slave_bresp),
  .cpu_slave_arready(cpu_slave_arready),
  .cpu_slave_arvalid(cpu_slave_arvalid),
  .cpu_slave_arid(cpu_slave_arid),
  .cpu_slave_araddr(cpu_slave_araddr),
  .cpu_slave_arlen(cpu_slave_arlen),
  .cpu_slave_arsize(cpu_slave_arsize),
  .cpu_slave_arburst(cpu_slave_arburst),
  .cpu_slave_rready(cpu_slave_rready),
  .cpu_slave_rvalid(cpu_slave_rvalid),
  .cpu_slave_rid(cpu_slave_rid),
  .cpu_slave_rdata(cpu_slave_rdata),
  .cpu_slave_rresp(cpu_slave_rresp),
  .cpu_slave_rlast(cpu_slave_rlast),
  .uart_rx(1'b1),
  .uart_tx()
);


ysyx_210153 cpu(
  .clock(clock),
  .reset(cpu_reset),
  .io_master_arid(cpu_master_0_arid),
  .io_master_araddr(cpu_master_0_araddr),
  .io_master_arlen(cpu_master_0_arlen),
  .io_master_arsize(cpu_master_0_arsize),
  .io_master_arburst(cpu_master_0_arburst),
  .io_master_arvalid(cpu_master_0_arvalid),
  .io_master_arready(cpu_master_0_arready),
  .io_master_rid(cpu_master_0_rid),
  .io_master_rdata(cpu_master_0_rdata),
  .io_master_rresp(cpu_master_0_rresp),
  .io_master_rlast(cpu_master_0_rlast),
  .io_master_rvalid(cpu_master_0_rvalid),
  .io_master_rready(cpu_master_0_rready),
  .io_master_awid(cpu_master_0_awid),
  .io_master_awaddr(cpu_master_0_awaddr),
  .io_master_awlen(cpu_master_0_awlen),
  .io_master_awsize(cpu_master_0_awsize),
  .io_master_awburst(cpu_master_0_awburst),
  .io_master_awvalid(cpu_master_0_awvalid),
  .io_master_awready(cpu_master_0_awready),
  .io_master_wdata(cpu_master_0_wdata),
  .io_master_wstrb(cpu_master_0_wstrb),
  .io_master_wlast(cpu_master_0_wlast),
  .io_master_wvalid(cpu_master_0_wvalid),
  .io_master_wready(cpu_master_0_wready),
  .io_master_bid(cpu_master_0_bid),
  .io_master_bresp(cpu_master_0_bresp),
  .io_master_bvalid(cpu_master_0_bvalid),
  .io_master_bready(cpu_master_0_bready),
  .io_slave_arid(cpu_slave_arid),
  .io_slave_araddr(cpu_slave_araddr),
  .io_slave_arlen(cpu_slave_arlen),
  .io_slave_arsize(cpu_slave_arsize),
  .io_slave_arburst(cpu_slave_arburst),
  .io_slave_arvalid(cpu_slave_arvalid),
  .io_slave_arready(cpu_slave_arready),
  .io_slave_rid(cpu_slave_rid),
  .io_slave_rdata(cpu_slave_rdata),
  .io_slave_rresp(cpu_slave_rresp),
  .io_slave_rlast(cpu_slave_rlast),
  .io_slave_rvalid(cpu_slave_rvalid),
  .io_slave_rready(cpu_slave_rready),
  .io_slave_awid(cpu_slave_awid),
  .io_slave_awaddr(cpu_slave_awaddr),
  .io_slave_awlen(cpu_slave_awlen),
  .io_slave_awsize(cpu_slave_awsize),
  .io_slave_awburst(cpu_slave_awburst),
  .io_slave_awvalid(cpu_slave_awvalid),
  .io_slave_awready(cpu_slave_awready),
  .io_slave_wdata(cpu_slave_wdata),
  .io_slave_wstrb(cpu_slave_wstrb),
  .io_slave_wlast(cpu_slave_wlast),
  .io_slave_wvalid(cpu_slave_wvalid),
  .io_slave_wready(cpu_slave_wready),
  .io_slave_bid(cpu_slave_bid),
  .io_slave_bresp(cpu_slave_bresp),
  .io_slave_bvalid(cpu_slave_bvalid),
  .io_slave_bready(cpu_slave_bready),
  .io_interrupt(cpu_intr)
);

endmodule

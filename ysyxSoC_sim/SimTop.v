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


ysyx_CPU cpu(
  .io_basic_ACLK(clock),
  .io_basic_ARESETn(cpu_reset),
  .io_memAXI_axiRa_ARID(cpu_master_0_arid),
  .io_memAXI_axiRa_ARADDR(cpu_master_0_araddr),
  .io_memAXI_axiRa_ARLEN(cpu_master_0_arlen),
  .io_memAXI_axiRa_ARSIZE(cpu_master_0_arsize),
  .io_memAXI_axiRa_ARBURST(cpu_master_0_arburst),
  .io_memAXI_axiRa_ARLOCK(),
  .io_memAXI_axiRa_ARCACHE(),
  .io_memAXI_axiRa_ARPROT(),
  .io_memAXI_axiRa_ARQOS(),
  .io_memAXI_axiRa_ARREGION(),
  .io_memAXI_axiRa_ARUSER(),
  .io_memAXI_axiRa_ARVALID(cpu_master_0_arvalid),
  .io_memAXI_axiRa_ARREADY(cpu_master_0_arready),
  .io_memAXI_axiRd_RID(cpu_master_0_rid),
  .io_memAXI_axiRd_RDATA(cpu_master_0_rdata),
  .io_memAXI_axiRd_RRESP(cpu_master_0_rresp),
  .io_memAXI_axiRd_RLAST(cpu_master_0_rlast),
  .io_memAXI_axiRd_RUSER(),
  .io_memAXI_axiRd_RVALID(cpu_master_0_rvalid),
  .io_memAXI_axiRd_RREADY(cpu_master_0_rready),
  .io_memAXI_axiWa_AWID(cpu_master_0_awid),
  .io_memAXI_axiWa_AWADDR(cpu_master_0_awaddr),
  .io_memAXI_axiWa_AWLEN(cpu_master_0_awlen),
  .io_memAXI_axiWa_AWSIZE(cpu_master_0_awsize),
  .io_memAXI_axiWa_AWBURST(cpu_master_0_awburst),
  .io_memAXI_axiWa_AWLOCK(),
  .io_memAXI_axiWa_AWCACHE(),
  .io_memAXI_axiWa_AWPROT(),
  .io_memAXI_axiWa_AWQOS(),
  .io_memAXI_axiWa_AWREGION(),
  .io_memAXI_axiWa_AWUSER(),
  .io_memAXI_axiWa_AWVALID(cpu_master_0_awvalid),
  .io_memAXI_axiWa_AWREADY(cpu_master_0_awready),
  .io_memAXI_axiWd_WDATA(cpu_master_0_wdata),
  .io_memAXI_axiWd_WSTRB(cpu_master_0_wstrb),
  .io_memAXI_axiWd_WLAST(cpu_master_0_wlast),
  .io_memAXI_axiWd_WUSER(),
  .io_memAXI_axiWd_WVALID(cpu_master_0_wvalid),
  .io_memAXI_axiWd_WREADY(cpu_master_0_wready),
  .io_memAXI_axiWr_BID(cpu_master_0_bid),
  .io_memAXI_axiWr_BRESP(cpu_master_0_bresp),
  .io_memAXI_axiWr_BUSER(),
  .io_memAXI_axiWr_BVALID(cpu_master_0_bvalid),
  .io_memAXI_axiWr_BREADY(cpu_master_0_bready),
  .io_dmaAXI_axiRa_ARID(cpu_slave_arid),
  .io_dmaAXI_axiRa_ARADDR(cpu_slave_araddr),
  .io_dmaAXI_axiRa_ARLEN(cpu_slave_arlen),
  .io_dmaAXI_axiRa_ARSIZE(cpu_slave_arsize),
  .io_dmaAXI_axiRa_ARBURST(cpu_slave_arburst),
  .io_dmaAXI_axiRa_ARLOCK(),
  .io_dmaAXI_axiRa_ARCACHE(),
  .io_dmaAXI_axiRa_ARPROT(),
  .io_dmaAXI_axiRa_ARQOS(),
  .io_dmaAXI_axiRa_ARREGION(),
  .io_dmaAXI_axiRa_ARUSER(),
  .io_dmaAXI_axiRa_ARVALID(cpu_slave_arvalid),
  .io_dmaAXI_axiRa_ARREADY(cpu_slave_arready),
  .io_dmaAXI_axiRd_RID(cpu_slave_rid),
  .io_dmaAXI_axiRd_RDATA(cpu_slave_rdata),
  .io_dmaAXI_axiRd_RRESP(cpu_slave_rresp),
  .io_dmaAXI_axiRd_RLAST(cpu_slave_rlast),
  .io_dmaAXI_axiRd_RUSER(),
  .io_dmaAXI_axiRd_RVALID(cpu_slave_rvalid),
  .io_dmaAXI_axiRd_RREADY(cpu_slave_rready),
  .io_dmaAXI_axiWa_AWID(cpu_slave_awid),
  .io_dmaAXI_axiWa_AWADDR(cpu_slave_awaddr),
  .io_dmaAXI_axiWa_AWLEN(cpu_slave_awlen),
  .io_dmaAXI_axiWa_AWSIZE(cpu_slave_awsize),
  .io_dmaAXI_axiWa_AWBURST(cpu_slave_awburst),
  .io_dmaAXI_axiWa_AWLOCK(),
  .io_dmaAXI_axiWa_AWCACHE(),
  .io_dmaAXI_axiWa_AWPROT(),
  .io_dmaAXI_axiWa_AWQOS(),
  .io_dmaAXI_axiWa_AWREGION(),
  .io_dmaAXI_axiWa_AWUSER(),
  .io_dmaAXI_axiWa_AWVALID(cpu_slave_awvalid),
  .io_dmaAXI_axiWa_AWREADY(cpu_slave_awready),
  .io_dmaAXI_axiWd_WDATA(cpu_slave_wdata),
  .io_dmaAXI_axiWd_WSTRB(cpu_slave_wstrb),
  .io_dmaAXI_axiWd_WLAST(cpu_slave_wlast),
  .io_dmaAXI_axiWd_WUSER(),
  .io_dmaAXI_axiWd_WVALID(cpu_slave_wvalid),
  .io_dmaAXI_axiWd_WREADY(cpu_slave_wready),
  .io_dmaAXI_axiWr_BID(cpu_slave_bid),
  .io_dmaAXI_axiWr_BRESP(cpu_slave_bresp),
  .io_dmaAXI_axiWr_BUSER(),
  .io_dmaAXI_axiWr_BVALID(cpu_slave_bvalid),
  .io_dmaAXI_axiWr_BREADY(cpu_slave_bready),
  .io_intr(cpu_intr)
);

endmodule
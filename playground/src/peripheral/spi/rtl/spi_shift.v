`include "spi_defines.v"

module spi_shift (clk, rst, latch, byte_sel, len, lsb, go, 
                  pos_edge, neg_edge, rx_negedge, tx_negedge,
                  tip, last, 
                  p_in, p_out, s_clk, s_in, s_out);
  
  input                          clk;          // system clock
  input                          rst;          // reset
  input                    [3:0] latch;        // latch signal for storing the data in shift register
  input                    [3:0] byte_sel;     // byte select signals for storing the data in shift register
  input [`SPI_CHAR_LEN_BITS-1:0] len;          // data len in bits (minus one)
  input                          lsb;          // lbs first on the line
  input                          go;           // start stansfer
  input                          pos_edge;     // recognize posedge of sclk
  input                          neg_edge;     // recognize negedge of sclk
  input                          rx_negedge;   // s_in is sampled on negative edge 
  input                          tx_negedge;   // s_out is driven on negative edge
  output                         tip;          // transfer in progress
  output                         last;         // last bit
  input                   [31:0] p_in;         // parallel in
  output     [`SPI_MAX_CHAR-1:0] p_out;        // parallel out
  input                          s_clk;        // serial clock
  input                          s_in;         // serial in
  output                         s_out;        // serial out
                                                   
  reg                            s_out;            
  reg                            tip;
                                     
  reg     [`SPI_CHAR_LEN_BITS:0] cnt;          // data bit count
  reg        [`SPI_MAX_CHAR-1:0] data;         // shift register
  wire    [`SPI_CHAR_LEN_BITS:0] tx_bit_pos;   // next bit position
  wire    [`SPI_CHAR_LEN_BITS:0] rx_bit_pos;   // next bit position
  wire                           rx_clk;       // rx clock enable
  wire                           tx_clk;       // tx clock enable
  wire    [`SPI_CHAR_LEN_BITS:0] tx_bit_pos_tmp;   // next bit position

  assign p_out = data;

  assign tx_bit_pos = lsb ? {!(|len), len} - cnt : cnt - {{`SPI_CHAR_LEN_BITS{1'b0}},1'b1};
  assign rx_bit_pos = lsb ? {!(|len), len} - (rx_negedge ? cnt + {{`SPI_CHAR_LEN_BITS{1'b0}},1'b1} : cnt) :
                            (rx_negedge ? cnt : cnt - {{`SPI_CHAR_LEN_BITS{1'b0}},1'b1});
  assign last = !(|cnt);

  assign rx_clk = (rx_negedge ? neg_edge : pos_edge) && (!last || s_clk);
  assign tx_clk = (tx_negedge ? neg_edge : pos_edge) && !last;

  // Character bit counter
  always @(posedge clk or posedge rst)
  begin
    if(rst)
      cnt <= {`SPI_CHAR_LEN_BITS+1{1'b0}};
    else
      begin
        if(tip)
          cnt <= pos_edge ? (cnt - {{`SPI_CHAR_LEN_BITS{1'b0}}, 1'b1}) : cnt;
        else
          cnt <= !(|len) ? {1'b1, {`SPI_CHAR_LEN_BITS{1'b0}}} : {1'b0, len};
      end
  end

  // Transfer in progress
  always @(posedge clk or posedge rst)
  begin
    if(rst)
      tip <= 1'b0;
  else if(go && ~tip)
    tip <= 1'b1;
  else if(tip && last && pos_edge)
    tip <= 1'b0;
  end

  // Sending bits to the line
  always @(posedge clk or posedge rst)
  begin
    if (rst)
      s_out   <= 1'b0;
    else
      s_out <= (tx_clk || !tip) ? data[tx_bit_pos[`SPI_CHAR_LEN_BITS-1:0]] : s_out;
  end

  // Receiving bits from the line
  always @(posedge clk or posedge rst)
  begin
    if (rst)
      data   <= {`SPI_MAX_CHAR{1'b0}};
`ifdef SPI_MAX_CHAR_128
    else if (latch[0] && !tip)
      begin
        if (byte_sel[3])
          data[31:24] <= p_in[31:24];
        if (byte_sel[2])
          data[23:16] <= p_in[23:16];
        if (byte_sel[1])
          data[15:8] <= p_in[15:8];
        if (byte_sel[0])
          data[7:0] <= p_in[7:0];
      end
    else if (latch[1] && !tip)
      begin
        if (byte_sel[3])
          data[63:56] <= p_in[31:24];
        if (byte_sel[2])
          data[55:48] <= p_in[23:16];
        if (byte_sel[1])
          data[47:40] <= p_in[15:8];
        if (byte_sel[0])
          data[39:32] <= p_in[7:0];
      end
    else if (latch[2] && !tip)
      begin
        if (byte_sel[3])
          data[95:88] <= p_in[31:24];
        if (byte_sel[2])
          data[87:80] <= p_in[23:16];
        if (byte_sel[1])
          data[79:72] <= p_in[15:8];
        if (byte_sel[0])
          data[71:64] <= p_in[7:0];
      end
    else if (latch[3] && !tip)
      begin
        if (byte_sel[3])
          data[127:120] <= p_in[31:24];
        if (byte_sel[2])
          data[119:112] <= p_in[23:16];
        if (byte_sel[1])
          data[111:104] <= p_in[15:8];
        if (byte_sel[0])
          data[103:96] <= p_in[7:0];
      end
`else
`ifdef SPI_MAX_CHAR_64
    else if (latch[0] && !tip)
      begin
        if (byte_sel[3])
          data[31:24] <= p_in[31:24];
        if (byte_sel[2])
          data[23:16] <= p_in[23:16];
        if (byte_sel[1])
          data[15:8] <= p_in[15:8];
        if (byte_sel[0])
          data[7:0] <= p_in[7:0];
      end
    else if (latch[1] && !tip)
      begin
        if (byte_sel[3])
          data[63:56] <= p_in[31:24];
        if (byte_sel[2])
          data[55:48] <= p_in[23:16];
        if (byte_sel[1])
          data[47:40] <= p_in[15:8];
        if (byte_sel[0])
          data[39:32] <= p_in[7:0];
      end
`else
    else if (latch[0] && !tip)
      begin
      `ifdef SPI_MAX_CHAR_8
        if (byte_sel[0])
          data[`SPI_MAX_CHAR-1:0] <= p_in[`SPI_MAX_CHAR-1:0];
      `endif
      `ifdef SPI_MAX_CHAR_16
        if (byte_sel[0])
          data[7:0] <= p_in[7:0];
        if (byte_sel[1])
          data[`SPI_MAX_CHAR-1:8] <= p_in[`SPI_MAX_CHAR-1:8];
      `endif
      `ifdef SPI_MAX_CHAR_24
        if (byte_sel[0])
          data[7:0] <= p_in[7:0];
        if (byte_sel[1])
          data[15:8] <= p_in[15:8];
        if (byte_sel[2])
          data[`SPI_MAX_CHAR-1:16] <= p_in[`SPI_MAX_CHAR-1:16];
      `endif
      `ifdef SPI_MAX_CHAR_32
        if (byte_sel[0])
          data[7:0] <= p_in[7:0];
        if (byte_sel[1])
          data[15:8] <= p_in[15:8];
        if (byte_sel[2])
          data[23:16] <= p_in[23:16];
        if (byte_sel[3])
          data[`SPI_MAX_CHAR-1:24] <= p_in[`SPI_MAX_CHAR-1:24];
      `endif
      end
`endif
`endif
    else
 data[rx_bit_pos[`SPI_CHAR_LEN_BITS-1:0]] <= rx_clk ? s_in : data[rx_bit_pos[`SPI_CHAR_LEN_BITS-1:0]];
  end

endmodule


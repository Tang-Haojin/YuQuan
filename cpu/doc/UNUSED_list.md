# Warning-UNUSED in verilator

| Signal | Instance | Ignore | Reason |
| --- | --- | --- | --- |
| io_master_rid | ysyx_210153 | yes | We send at most a read and a write request at the same time, so id is not needed. |
| io_master_rresp | ysyx_210153 | yes | We do not handle AXI error now. |
| io_slave_arvalid | ysyx_210153 | yes | DMA controller may not read from core. |
| io_slave_arid | ysyx_210153 | yes | DMA controller may not read from core. |
| io_slave_araddr | ysyx_210153 | yes | DMA controller may not read from core. |
| io_slave_arlen | ysyx_210153 | yes | DMA controller may not read from core. |
| io_slave_arsize | ysyx_210153 | yes | DMA controller may not read from core. |
| io_slave_arburst | ysyx_210153 | yes | DMA controller may not read from core. |
| io_slave_rready | ysyx_210153 | yes | DMA controller may not read from core. |
| io_csrsR_rdata_2 | ysyx_210153.moduleID | yes | This is Mie read data, and we only use part of its bits. |
| io_csrsR_rdata_5 | ysyx_210153.moduleID | yes | This is Mtvec read data, and we only use part of its bits. |
| io_csrsR_rdata_7 | ysyx_210153.moduleID | yes | This is Mip read data, and we only use part of its bits. |
| sl | ysyx_210153.moduleEX.alu | yes | This is used both for sll and sllw, while sllw only the low 32 bits. |

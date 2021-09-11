pwd = $(shell pwd)
cpuNum = $(shell echo $$((`lscpu -p=CORE | tail -n 1` + 1)))
ysyx_path = $(pwd)/../ysyxSoC/ysyx
peripheral_path = $(ysyx_path)/peripheral

# make it sure that ysyxSoC path looks sane
ifeq ($(wildcard $(ysyx_path)/soc/ysyxSoCFull.v),)
	$(error $(shell echo "\e[31m`ysyxSoC` not found! Please init and update submodule correctly\e[0m"))
endif

ifeq ($(TRACE),1)
VFLAGS += --trace-fst --trace-threads 2
CFLAGS += -DTRACE
endif

CFLAGS += -DD
VSRCS  += $(shell find $(pwd)/../build/cpu | grep -xPo '.*\.v')
VSRCS  += $(shell find $(peripheral_path) | grep -xPo '.*\.v')
VSRCS  += $(pwd)/ysyxSoCFull.v
VFLAGS += -I$(peripheral_path)/uart16550/rtl -I$(peripheral_path)/spi/rtl
CSRCS  += $(peripheral_path)/spiFlash/spiFlash.cpp $(pwd)/sim_main.cpp
VFLAGS += --timescale "1ns/1ns" -Wno-WIDTH --top ysyxSoCFull --exe -j $(cpuNum)
VFLAGS += --threads $(cpuNum) -cc $(VSRCS)

build:
	@cp $(ysyx_path)/soc/ysyxSoCFull.v $(pwd)
	@sed -i '/  ysyx_000000 cpu (/c\  ysyx_210153 cpu (' $(pwd)/ysyxSoCFull.v
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)"
	@rm -f $(pwd)/ysyxSoCFull.v

clean:
	-rm -rf $(pwd)/obj_dir

.PHONY: build clean
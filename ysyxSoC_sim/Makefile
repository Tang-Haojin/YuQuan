pwd = $(shell pwd)
cpuNum = $(shell echo $$((`lscpu -p=CORE | tail -n 1` + 1)))
peripheral_path = $(pwd)/../ysyxSoC/src/main/resources/ysyx-peripheral

# make it sure that ysyxSoC path looks sane
ifeq ($(wildcard $(peripheral_path)/ysyxSoCFull.v),)
	$(error $(shell echo "\e[31m`ysyxSoC` not found! Please init and update submodule correctly\e[0m"))
endif

ifeq ($(TRACE),1)
VFLAGS += --trace-fst --trace-threads 2
CFLAGS += -DTRACE
endif

CFLAGS += -DD
VSRCS  += $(pwd)/SimTop.v
VSRCS  += $(shell find $(pwd)/../build/cpu | grep -xPo '.*\.v')
VSRCS  += $(shell find $(peripheral_path) | grep -xPo '.*\.v')
VFLAGS += -I$(peripheral_path)/uart16550/rtl -I$(peripheral_path)/spi/rtl
CSRCS  += $(peripheral_path)/spiFlash/spiFlash.cpp $(pwd)/sim_main.cpp
VFLAGS += --timescale "1ns/1ns" -Wno-WIDTH --top SimTop --exe -j $(cpuNum)
VFLAGS += --threads $(cpuNum) -cc $(VSRCS)

sim:
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)"

clean:
	-rm -rf $(pwd)/obj_dir

.PHONY: sim clean
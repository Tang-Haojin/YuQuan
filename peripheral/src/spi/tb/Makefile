pwd = $(shell pwd)
simtop = tb

BIN    ?= $(pwd)/rtthread-riscv64-nemu.bin
VSRCS  += $(pwd)/$(simtop).sv $(wildcard $(pwd)/../../spiFlash/*.sv) $(wildcard $(pwd)/../rtl/*.v) $(wildcard $(pwd)/../../axi2apb/*.v)
VFLAGS += --top $(simtop) --exe --timescale "1ns/1ns" -I$(pwd)/../rtl -Wno-WIDTH --trace-fst
CSRCS  += $(pwd)/$(simtop).cpp $(pwd)/../../spiFlash/spiFlash.cpp

sim: build
	$(pwd)/obj_dir/V$(simtop) $(BIN)

build: $(pwd)/*.sv $(pwd)/*.cpp $(pwd)/../../spiFlash/spiFlash.cpp $(pwd)/../rtl/*.v $(pwd)/../../axi2apb/*.v $(pwd)/../../spiFlash/*.sv
	verilator -cc $(VSRCS) $(VFLAGS) --build $(CSRCS)

clean:
	rm -rf obj_dir dump.fst

.PHONY: sim build clean

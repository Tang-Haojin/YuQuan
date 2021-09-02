pwd := $(shell pwd)
NO_ERR = >>/dev/null 2>&1 | echo >>/dev/null 2>&1
site = https://tanghaojin.site/static
BUILD_DIR = ./build
LIB_DIR   = $(pwd)/sim/lib
simSrcDir = $(pwd)/sim/src
srcDir    = $(pwd)/cpu/src
cpuNum    = $(shell echo $$((`lscpu -p=CORE | tail -n 1` + 1)))
nobin     = $(shell echo "\e[31mNo BIN file specified\e[0m")
$(shell mkdir -p $(pwd)/sim/bin $(NO_ERR))
$(shell cat .config >>/dev/null 2>&1 || echo >.config)

ISA := riscv64

ifeq ($(FLASH),1)
param += FLASH
CFLAGS += -DFLASH
endif

ifeq ($(STORAGE),1)
CFLAGS += -DSTORAGE
endif

ifeq ($(CHIPLINK),1)
param += CHIPLINK
override UART = 1
VFLAGS += --threads $(cpuNum)
endif

ifeq ($(UART),1)
param += UART
CFLAGS += -DUART
endif

CSRCS   += $(simSrcDir)/sim_main.cpp $(simSrcDir)/peripheral/ram/ram.cpp
CSRCS   += $(simSrcDir)/peripheral/spiFlash/spiFlash.cpp
CSRCS   += $(simSrcDir)/peripheral/storage/storage.cpp
CSRCS   += $(simSrcDir)/peripheral/uart/scanKbd.cpp
CSRCS   += $(simSrcDir)/peripheral/uart/uart.cpp

CFLAGS  += -D$(ISA) -pthread -I$(pwd)/sim/include
LDFLAGS += -pthread

VFLAGS  += --top TestTop --exe --timescale "1ns/1ns" -Wno-WIDTH
VFLAGS  += -I$(pwd)/peripheral/src/uart16550
VFLAGS  += -I$(pwd)/utils/src/axi2apb/inner
VFLAGS  += -I$(pwd)/peripheral/src/spi/rtl -j $(cpuNum) -O3
VFLAGS  += -I$(simSrcDir)/peripheral/spiFlash
VFLAGS  += -cc TestTop.v $(pwd)/peripheral/src/chiplink/chiplink.v $(pwd)/peripheral/src/chiplink/top.v

ifeq ($(TRACE),1)
VFLAGS += --trace-fst --trace-threads 2
CFLAGS += -DTRACE
endif

DIFF ?= 1
ifeq ($(DIFF),0)
else
LIBNEMU = $(LIB_DIR)/librv64nemu.so
$(shell mkdir $(LIB_DIR) $(NO_ERR))
ifeq ($(wildcard $(LIBNEMU)),)
$(shell wget $(site)/librv64nemu.so -O $(LIBNEMU) || rm $(LIBNEMU))
endif
export LD_LIBRARY_PATH := $(LIB_DIR):$(LD_LIBRARY_PATH)
LDFLAGS += -L$(LIB_DIR) -lrv64nemu -lSDL2 -lreadline
CFLAGS  += -DDIFFTEST
endif

ifneq ($(BIN),)
binFile = $(pwd)/sim/bin/$(BIN)-$(ISA)-nemu.bin
flashBinFile = $(pwd)/sim/bin/$(BIN)~flash-$(ISA)-nemu.bin
storageBinFile = $(pwd)/sim/bin/$(BIN)~storage-$(ISA)-nemu.bin
ifeq ($(wildcard $(binFile)),)
$(shell wget $(site)/$(BIN)-$(ISA)-nemu.bin -O $(binFile) || rm $(binFile))
endif
endif

SIMBIN = $(filter-out rtthread,$(shell cd $(pwd)/sim/bin && ls *-$(ISA)-nemu.bin | grep -oP ".*(?=-$(ISA)-nemu.bin)"))

test:
	mill -i __.test

verilog:
	mill -i __.cpu.runMain Elaborate -td $(BUILD_DIR)/cpu

help:
	mill -i __.sim.runMain Elaborate --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

ysyxcheck: verilog
	@echo
	@cp $(pwd)/ysyxSoC/ysyx/soc/cpu-check.py $(pwd)/.cpu-check.py
	@cp $(BUILD_DIR)/cpu/ysyx_21*.v $(pwd)
	@sed -i '/stuNum = /c\stuNum = int(153)' $(pwd)/.cpu-check.py
	@python3 $(pwd)/.cpu-check.py
	@rm $(pwd)/.cpu-check.py $(pwd)/ysyx_21*.v

clean:
	-rm -rf $(BUILD_DIR)

clean-all: clean
	-rm -rf ./out

sim:
ifeq ($(BIN),)
	$(error $(nobin))
endif
	mill -i __.sim.runMain Elaborate -td build/sim $(param)
	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null
	@$(BUILD_DIR)/sim/obj_dir/VTestTop $(binFile) $(flashBinFile) $(storageBinFile)

simall:
	mill -i __.sim.runMain Elaborate -td build/sim $(param)
	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null
	@for x in $(SIMBIN); do \
		$(BUILD_DIR)/sim/obj_dir/VTestTop $(pwd)/sim/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else                   printf "[$$x] \33[1;31mfail\33[0m\n"; fi; \
	done

.PHONY: test verilog help compile bsp reformat checkformat ysyxcheck clean clean-all sim simall

pwd := $(shell pwd)
NO_ERR = >>/dev/null 2>&1 | echo >>/dev/null 2>&1
site = https://tanghaojin.site/static
BUILD_DIR = ./build
LIB_DIR   = $(pwd)/difftest/difftest/build
simSrcDir = $(pwd)/sim/src
srcDir    = $(pwd)/cpu/src
cpuNum    = $(shell echo $$((`lscpu -p=CORE | tail -n 1` + 1)))
nobin     = $(shell echo "\e[31mNo BIN file specified\e[0m")
$(shell mkdir -p $(pwd)/sim/bin $(NO_ERR))

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
CSRCS   += $(simSrcDir)/peripheral/sdcard/sdcard.cpp

CFLAGS  += -D$(ISA) -pthread -I$(pwd)/sim/include
LDFLAGS += -pthread

VFLAGS  += --top TestTop --exe --timescale "1ns/1ns" -Wno-WIDTH
VFLAGS  += -I$(pwd)/peripheral/src/uart16550
VFLAGS  += -I$(pwd)/utils/src/axi2apb/inner
VFLAGS  += -I$(pwd)/peripheral/src/spi/rtl -j $(cpuNum) -O3
VFLAGS  += -I$(simSrcDir)/peripheral/spiFlash
VFLAGS  += -I$(simSrcDir)/peripheral/sdcard
VFLAGS  += -cc TestTop.v $(pwd)/peripheral/src/chiplink/chiplink.v $(pwd)/peripheral/src/chiplink/top.v

ifeq ($(TRACE),1)
VFLAGS += --trace-fst --trace-threads 2
CFLAGS += -DTRACE
endif

DIFF ?= 1
ifneq ($(DIFF),1)
else
LIB_SPIKE = $(LIB_DIR)/librv64spike.so
$(shell mkdir $(LIB_DIR) $(NO_ERR))
export LD_LIBRARY_PATH := $(LIB_DIR):$(LD_LIBRARY_PATH)
LDFLAGS += -L$(LIB_DIR) -lrv64spike -ldl
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

SIMBIN = $(filter-out yield rtthread fw_payload xv6 xv6-full linux linux-c debian debian-disk,$(shell cd $(pwd)/sim/bin && ls *-$(ISA)-nemu.bin | grep -oP ".*(?=-$(ISA)-nemu.bin)"))

ifneq ($(mainargs),)
CFLAGS += '-Dmainargs=$(mainargs)'
endif

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
	@verilator --lint-only --top-module ysyx_210153 -Wall -Wno-DECLFILENAME $(shell find $(BUILD_DIR)/cpu/*.v)

clean:
	-rm -rf $(BUILD_DIR)

clean-all: clean
	-rm -rf ./out ./difftest/build ./difftest/difftest/build

verilate:
	mill -i __.sim.runMain Elaborate -td build/sim $(param)
	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null

sim: $(LIB_SPIKE) verilate
ifeq ($(BIN),)
	$(error $(nobin))
endif
	@$(BUILD_DIR)/sim/obj_dir/VTestTop $(binFile) $(flashBinFile) $(storageBinFile)

simall: $(LIB_SPIKE) verilate
	@for x in $(SIMBIN); do \
		$(BUILD_DIR)/sim/obj_dir/VTestTop $(pwd)/sim/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else                   printf "[$$x] \33[1;31mfail\33[0m\n"; fi; \
	done

zmb:
	mill -i __.cpu.runMain Elaborate -td $(BUILD_DIR)/zmb zmb
	@cat $(BUILD_DIR)/zmb/SimTop.v >>$(BUILD_DIR)/zmb/`cd $(BUILD_DIR)/zmb/ && ls S011HD1P_X32Y*`
	@mv -f $(BUILD_DIR)/zmb/`cd $(BUILD_DIR)/zmb/ && ls S011HD1P_X32Y*` $(BUILD_DIR)/zmb/SimTop.v
	@cp $(BUILD_DIR)/zmb/SimTop.v $(BUILD_DIR)/zmb/riscv_cpu.v
	@sed -i -e 's/io_master_/io_memAXI_0_/g' $(BUILD_DIR)/zmb/SimTop.v
	@sed -i -e 's/module SimTop/module riscv_cpu/g' $(BUILD_DIR)/zmb/riscv_cpu.v
	@sed -i -e 's/io_master_/io_mem_/g' $(BUILD_DIR)/zmb/riscv_cpu.v
	@sed -i -e 's/io_perfInfo_dump,/io_meip/g' $(BUILD_DIR)/zmb/riscv_cpu.v
	@sed -i '/io_logCtrl/d' $(BUILD_DIR)/zmb/riscv_cpu.v
	@sed -i '/io_perfInfo/d' $(BUILD_DIR)/zmb/riscv_cpu.v
	@sed -i '/io_uart/d' $(BUILD_DIR)/zmb/riscv_cpu.v
	

$(LIB_DIR)/librv64spike.so:
	@cd $(pwd)/difftest/difftest && make -j && cd build && ln -sf riscv64-spike-so librv64spike.so

.PHONY: test verilog help compile bsp reformat checkformat ysyxcheck clean clean-all verilate sim simall zmb $(LIB_DIR)/librv64spike.so

pwd := $(shell pwd)
NO_ERR = >>/dev/null 2>&1 | echo >>/dev/null 2>&1
BUILD_DIR = $(pwd)/build
OBJ_DIR  ?= $(BUILD_DIR)/sim/obj_dir
LIB_DIR   = $(pwd)/difftest/difftest/build
simSrcDir = $(pwd)/sim/src
srcDir    = $(pwd)/cpu/src
cpuNum    = $(shell echo $$((`lscpu -p=CORE | tail -n 1` + 1)))
nobin     = $(shell echo "\e[31mNo BIN file specified\e[0m")

ISA := riscv64

ifeq ($(FLASH),1)
param += FLASH
CFLAGS += -DFLASH
endif

ifeq ($(ARCHIVE),)
CSRCS   += $(simSrcDir)/sim_main.cpp $(simSrcDir)/peripheral/ram/ram.cpp
CSRCS   += $(simSrcDir)/peripheral/spiFlash/spiFlash.cpp
CSRCS   += $(simSrcDir)/peripheral/uart/scanKbd.cpp
CSRCS   += $(simSrcDir)/peripheral/uart/uart.cpp
CSRCS   += $(simSrcDir)/peripheral/sdcard/sdcard.cpp
endif

CFLAGS  += -D$(ISA) -pthread -I$(pwd)/sim/include
LDFLAGS += -pthread

TOP ?= TestTop
TOP_FILE_PATH = $(BUILD_DIR)/sim/$(TOP).sv
VERILATOR_TARGET = $(OBJ_DIR)/V$(TOP)

ifeq ($(ARCHIVE),)
VFLAGS  += --exe
endif
VFLAGS  += --top $(TOP) --timescale "1ns/1ns" -Wno-WIDTH
VFLAGS  += -I$(pwd)/peripheral/src/uart16550
VFLAGS  += -I$(pwd)/utils/src/axi2apb/inner
VFLAGS  += -I$(pwd)/peripheral/src/spi/rtl -j $(cpuNum) -O3
VFLAGS  += -I$(simSrcDir)/peripheral/spiFlash
VFLAGS  += -I$(simSrcDir)/peripheral/sdcard
VFLAGS  += -Mdir $(OBJ_DIR)
VFLAGS  += -cc $(TOP).sv

ifeq ($(TRACE),1)
VFLAGS += --trace-fst --trace-threads 2 --trace-underscore
CFLAGS += -DTRACE
endif

ifeq ($(CORVUS),1)
param += HW
REPCUT_NUM ?= 8
CORVUS_ARGS += --repcut-num-partitions=$(REPCUT_NUM)
endif

ifneq ($(CORVUSITOR_PATH),)
CORVUSITOR_REAL_PATH = $(CORVUSITOR_PATH)
else
CORVUSITOR_REAL_PATH = corvusitor
endif

ifneq ($(CORVUS_PATH),)
CORVUS_REAL_PATH = $(CORVUS_PATH)
else
CORVUS_REAL_PATH = corvus-compiler
endif

ZMB ?= 0
ifeq ($(ZMB),0)
DIFF ?= 1
GENNAME = ysyx
else
DIFF ?= 0
GENNAME = zmb
endif

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
endif

SIMBIN = $(filter-out yield rtthread fw_payload xv6 xv6-cake xv6-full dma-c dma-large-c dma-multi-c linux linux-c debian debian-disk,$(shell cd $(pwd)/sim/bin && ls *-$(ISA)-nemu.bin | grep -oP ".*(?=-$(ISA)-nemu.bin)"))

ifneq ($(mainargs),)
CFLAGS += '-Dmainargs=$(mainargs)'
endif

PRETTY =

test:
	mill -i __.test

verilog:
	mill -i cpu.runMain cpu.top.Elaborate args -td $(BUILD_DIR)/cpu $(PRETTY)
	@sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/cpu/*

help:
	mill -i sim.runMain sim.top.Elaborate --help

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

$(TOP_FILE_PATH):
	mill -i sim.runMain sim.top.Elaborate args -td $(BUILD_DIR)/sim $(GENNAME) $(param)
ifeq ($(CORVUS),1)
	@$(CORVUS_REAL_PATH) $(BUILD_DIR)/sim/$(TOP).hw.mlir --split-verilog $(CORVUS_ARGS) -o $(BUILD_DIR)/sim
endif

verilate: $(TOP_FILE_PATH)
	cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null

ifeq ($(CORVUSITOR),1)
SIMULATE = corvusitor
else
SIMULATE = verilate
endif

sim: $(LIB_SPIKE) $(SIMULATE)
ifeq ($(CORVUSITOR),1)
	$(MAKE) -C $(BUILD_DIR)/sim/corvusitor-compile sim BIN=$(BIN)
else
ifeq ($(BIN),)
	$(error $(nobin))
endif
	@$(VERILATOR_TARGET) $(binFile) $(flashBinFile)
endif

simall: $(LIB_SPIKE) $(SIMULATE)
	@for x in $(SIMBIN); do \
		$(VERILATOR_TARGET) $(pwd)/sim/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else                   printf "[$$x] \33[1;31mfail\33[0m\n"; fi; \
	done

zmb:
	mill -i cpu.runMain cpu.top.Elaborate args -td $(BUILD_DIR)/zmb zmb $(PRETTY)

lxb:
	mill -i cpu.runMain cpu.top.Elaborate args -td $(BUILD_DIR)/lxb lxb $(PRETTY)

rv64: verilog

la32r: lxb
	

$(LIB_SPIKE):
	@cd $(pwd)/difftest/difftest && make -j && cd build && ln -sf riscv64-spike-so librv64spike.so

CORVUS_MODULE_FILES := $(shell seq -f "$(BUILD_DIR)/sim/corvus_comb_P%g.sv" 0 $$(($(REPCUT_NUM)-1))) \
                       $(shell seq -f "$(BUILD_DIR)/sim/corvus_seq_P%g.sv" 0 $$(($(REPCUT_NUM)-1))) \
                       $(BUILD_DIR)/sim/corvus_external.sv

define RUN_CORVUS_MODULE
.corvus.run.$1: $(TOP_FILE_PATH)
	@echo "Running Corvus on module $(basename $(notdir $1))"
	$(MAKE) verilate ARCHIVE=1 OBJ_DIR=$(BUILD_DIR)/sim/verilator-compile-$(basename $(notdir $1)) TOP=$(basename $(notdir $1))
endef

verilate-archive: $(TOP_FILE_PATH) $(CORVUS_MODULE_FILES:%=.corvus.run.%)

$(foreach f,$(CORVUS_MODULE_FILES),$(eval $(call RUN_CORVUS_MODULE,$f)))

corvusitor: verilate-archive
	mkdir -p $(BUILD_DIR)/sim/corvusitor-compile
	cp $(simSrcDir)/sim_main_corvus.mk $(BUILD_DIR)/sim/corvusitor-compile/Makefile
	$(CORVUSITOR_REAL_PATH) -m $(BUILD_DIR)/sim -o $(BUILD_DIR)/sim/corvusitor-compile/VCorvusTopWrapper_generated.cpp
	@$(MAKE) -C $(BUILD_DIR)/sim/corvusitor-compile _CORVUS_all

.PHONY: test verilog help compile bsp reformat checkformat ysyxcheck clean clean-all verilate sim simall zmb lxb rv64 la32r $(LIB_DIR)/librv64spike.so corvusitor
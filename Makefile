pwd = $(shell pwd)
site = https://tanghaojin.site/static
BUILD_DIR = ./build
ROOT_DIR  = $(shell cat build.sc | grep -oP "(?<=object ).*(?= extends ScalaModule)")
SUB_DIR   = $(shell cd $(ROOT_DIR); ls -d */ | tr -d / | grep -v src; cd ..)
LIB_DIR   = $(pwd)/$(ROOT_DIR)/sim/lib
SSRC_DIR  = $(pwd)/$(ROOT_DIR)/sim/src
$(shell mkdir $(ROOT_DIR)/sim/bin >>/dev/null 2>&1 | echo >>/dev/null 2>&1)

ifeq ($(ISA),)
ISA = riscv64
xlens = 64
export XLEN = 64
endif
ifeq ($(ISA),riscv32)
xlens = 32
export XLEN = 32
endif

ifeq ($(UART),1)
export UART = 1
CSRCS  += $(SSRC_DIR)/cpu/csrc/scanKbd.cpp
CFLAGS += -DUART
else
export UART = 0
CSRCS  += $(SSRC_DIR)/cpu/csrc/uart.cpp
endif

CSRCS   += $(SSRC_DIR)/sim_main.cpp $(SSRC_DIR)/cpu/csrc/ram.cpp
CFLAGS  += -D$(ISA) -pthread -I$(pwd)/$(ROOT_DIR)/sim/include
LDFLAGS += -pthread
VFLAGS  += -cc TestTop.v --top TestTop --exe --timescale "1ns/1ns" -Wno-WIDTH -I$(pwd)/$(ROOT_DIR)/src/cpu/vsrc/peripheral

ifeq ($(TRACE),1)
VFLAGS += --trace-fst
CFLAGS += DTRACE
endif

ifneq ($(DIFF),)
export DIFF = 0
else
LIBNEMU = $(LIB_DIR)/librv$(xlens)nemu.so
$(shell mkdir $(LIB_DIR) >>/dev/null 2>&1 | echo >>/dev/null 2>&1)
ifeq ($(wildcard $(LIBNEMU)),)
$(shell wget $(site)/librv64nemu.so -O $(LIBNEMU) || rm $(LIBNEMU))
endif
export LD_LIBRARY_PATH := $(LIB_DIR):$(LD_LIBRARY_PATH)
LDFLAGS += -L$(pwd)/$(ROOT_DIR)/sim/lib -lrv64nemu -lSDL2 -lreadline
CFLAGS  += -DDIFFTEST
endif

ifneq ($(BIN),)
BINFILE = $(pwd)/$(ROOT_DIR)/sim/bin/$(BIN)-$(ISA)-nemu.bin
ifeq ($(wildcard $(BINFILE)),)
$(shell wget $(site)/$(BIN)-$(ISA)-nemu.bin -O $(BINFILE) || rm $(BINFILE))
endif
endif

test:
	mill -i __.test

verilog:
	@for x in $(SUB_DIR); do \
		mill -i __.$$x.runMain Elaborate -td $(BUILD_DIR)/$$x; \
	done

help:
	mill -i __.test.runMain Elaborate --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	-rm -rf $(BUILD_DIR)

sim:
	mill -i __.sim.runMain Elaborate -td $(BUILD_DIR)/sim

	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null

	@$(BUILD_DIR)/sim/obj_dir/VTestTop $(BINFILE)

simall:
	@for x in `cd $(ROOT_DIR)/sim/bin && ls *-$(ISA)-nemu.bin | grep -oP ".*(?=-$(ISA)-nemu.bin)"`; do \
		make BIN=$$x ISA=$(ISA) sim >/dev/null 2>&1; \
		cd $(BUILD_DIR)/sim && ./obj_dir/VTestTop $(pwd)/$(ROOT_DIR)/sim/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then \
			printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else \
			printf "[$$x] \33[1;31mfail\33[0m\n"; \
		fi; \
		cd $(pwd); \
	done

.PHONY: test verilog help compile bsp reformat checkformat clean sim simall

pwd = $(shell pwd)
NO_ERR = >>/dev/null 2>&1 | echo >>/dev/null 2>&1
site = https://tanghaojin.site/static
BUILD_DIR = ./build
ROOT_DIR  = $(shell cat build.sc | grep -oP "(?<=object ).*(?= extends ScalaModule)")
SUB_DIR   = $(shell cd $(ROOT_DIR); ls -d */ | tr -d / | grep -v src; cd ..)
LIB_DIR   = $(pwd)/$(ROOT_DIR)/sim/lib
SSRC_DIR  = $(pwd)/$(ROOT_DIR)/sim/src
$(shell mkdir $(ROOT_DIR)/sim/bin $(NO_ERR))

ifeq ($(ISA),)
ISA = riscv64
xlens = 64
endif
ifeq ($(ISA),riscv32)
xlens = 32
endif
export XLEN = $(xlens)

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
$(shell mkdir $(LIB_DIR) $(NO_ERR))
ifeq ($(wildcard $(LIBNEMU)),)
$(shell wget $(site)/librv64nemu.so -O $(LIBNEMU) || rm $(LIBNEMU))
endif
export LD_LIBRARY_PATH := $(LIB_DIR):$(LD_LIBRARY_PATH)
LDFLAGS += -L$(LIB_DIR) -lrv64nemu -lSDL2 -lreadline
CFLAGS  += -DDIFFTEST
endif

ifneq ($(BIN),)
BINFILE = $(pwd)/$(ROOT_DIR)/sim/bin/$(BIN)-$(ISA)-nemu.bin
ifeq ($(wildcard $(BINFILE)),)
$(shell wget $(site)/$(BIN)-$(ISA)-nemu.bin -O $(BINFILE) || rm $(BINFILE))
endif
endif

SIMBIN = $(filter-out rtthread,$(shell cd $(ROOT_DIR)/sim/bin && ls *-$(ISA)-nemu.bin | grep -oP ".*(?=-$(ISA)-nemu.bin)"))

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

clean-all: clean
	-rm -rf ./out

sim-env:
	mill -i __.sim.runMain Elaborate -td $(BUILD_DIR)/sim
	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null

sim: sim-env
	@$(BUILD_DIR)/sim/obj_dir/VTestTop $(BINFILE)

simall: sim-env
	@for x in $(SIMBIN); do \
		$(BUILD_DIR)/sim/obj_dir/VTestTop $(ROOT_DIR)/sim/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else                   printf "[$$x] \33[1;31mfail\33[0m\n"; fi; \
	done

.PHONY: test verilog help compile bsp reformat checkformat clean clean-all sim-env sim simall

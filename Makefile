pwd = $(shell pwd)
NO_ERR = >>/dev/null 2>&1 | echo >>/dev/null 2>&1
site = https://tanghaojin.site/static
BUILD_DIR = ./build
ROOT_DIR  = $(shell cat build.sc | grep -oP "(?<=object ).*(?= extends ScalaModule)")
SUB_DIR   = $(shell cd $(ROOT_DIR); ls -d */ | tr -d / | grep -v src; cd ..)
LIB_DIR   = $(pwd)/$(ROOT_DIR)/sim/lib
SSRC_DIR  = $(pwd)/$(ROOT_DIR)/sim/src
SRCS      = $(shell find $(ROOT_DIR) | grep -xPo '.*\.(v|c|h|cpp|hpp|scala)')
ALL_C     = $(shell echo $(SRCS) | grep -xPo '.*\.(c|h|cpp|hpp)')
ALL_SCALA = $(shell echo $(SRCS) | grep -xPo '.*\.(v|scala)')
$(shell mkdir $(ROOT_DIR)/sim/bin $(NO_ERR))
$(shell cat .config >>/dev/null 2>&1 || echo >.config)

ifeq ($(ISA),)
ISA = riscv64
xlens = 64
endif
ifeq ($(ISA),riscv32)
xlens = 32
endif
export XLEN = $(xlens)

UART ?= 0
ifeq ($(UART),1)
export UART = 1
CSRCS  += $(SSRC_DIR)/peripheral/uart/scanKbd.cpp
CFLAGS += -DUART
else
export UART = 0
CSRCS  += $(SSRC_DIR)/peripheral/uart/uart.cpp
endif

ifneq ($(shell cat .config | grep 'UART'),UART=$(UART))
$(shell rm -rf $(BUILD_DIR) out)
endif

CSRCS   += $(SSRC_DIR)/sim_main.cpp $(SSRC_DIR)/peripheral/ram/ram.cpp
CFLAGS  += -D$(ISA) -pthread -I$(pwd)/$(ROOT_DIR)/sim/include
LDFLAGS += -pthread
VFLAGS  += -cc TestTop.v --top TestTop --exe --timescale "1ns/1ns" -Wno-WIDTH -I$(pwd)/$(ROOT_DIR)/src/peripheral/uart16550

TRACE ?= 0
ifeq ($(TRACE),1)
VFLAGS += --trace-fst
CFLAGS += -DTRACE
endif

ifneq ($(shell cat .config | grep 'TRACE'),TRACE=$(TRACE))
$(shell rm -rf $(BUILD_DIR))
endif

DIFF ?= 1
ifeq ($(DIFF),0)
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

ifneq ($(shell cat .config | grep 'DIFF'),DIFF=$(DIFF))
$(shell rm -rf $(BUILD_DIR))
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

$(BUILD_DIR)/sim/*.v: $(ALL_SCALA)
	mill -i __.sim.runMain Elaborate -td $(BUILD_DIR)/sim
	@echo DIFF=$(DIFF) >.config
	@echo UART=$(UART) >>.config
	@echo TRACE=$(TRACE) >>.config

$(BUILD_DIR)/sim/obj_dir/VTestTop: $(BUILD_DIR)/sim/*.v $(ALL_C)
	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null

sim: $(BUILD_DIR)/sim/obj_dir/VTestTop
	@$(BUILD_DIR)/sim/obj_dir/VTestTop $(BINFILE)

simall: $(BUILD_DIR)/sim/obj_dir/VTestTop
	@for x in $(SIMBIN); do \
		$(BUILD_DIR)/sim/obj_dir/VTestTop $(ROOT_DIR)/sim/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else                   printf "[$$x] \33[1;31mfail\33[0m\n"; fi; \
	done

.PHONY: test verilog help compile bsp reformat checkformat clean clean-all sim simall

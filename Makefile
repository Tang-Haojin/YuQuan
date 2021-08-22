pwd = $(shell pwd)
NO_ERR = >>/dev/null 2>&1 | echo >>/dev/null 2>&1
site = https://tanghaojin.site/static
BUILD_DIR = ./build
ROOT_DIR  = $(shell cat build.sc | grep -oP "(?<=object ).*(?= extends ScalaModule)")
SUB_DIR   = $(shell cd $(ROOT_DIR); ls -d */ | tr -d / | grep -v src; cd ..)
SIM_DIR   = $(pwd)/$(ROOT_DIR)/sim
LIB_DIR   = $(SIM_DIR)/lib
SSRC_DIR  = $(SIM_DIR)/src
SRC_DIR   = $(pwd)/$(ROOT_DIR)/src
SRCS      = $(shell find $(ROOT_DIR) | grep -xPo '.*\.(sv|v|c|h|cpp|hpp|scala)')
ALL_C     = $(shell echo $(SRCS) | grep -xPo '.*\.(c|h|cpp|hpp)')
ALL_SCALA = $(shell echo $(SRCS) | grep -xPo '.*\.(sv|v|scala)')
CPU_NUM   = $(shell echo $$((`lscpu -p=CORE | tail -n 1` + 1)))
$(shell mkdir $(SIM_DIR)/bin $(NO_ERR))
$(shell cat .config >>/dev/null 2>&1 || echo >.config)

ifeq ($(ISA),riscv32)
xlens = 32
else
xlens = 64
endif
export XLEN = $(xlens)

FLASH ?= 0
ifeq ($(FLASH),1)
export FLASH = 1
CFLAGS += -DFLASH
else
export FLASH = 0
endif

ifneq ($(shell cat .config | grep 'FLASH'),FLASH=$(FLASH))
$(shell rm -rf $(BUILD_DIR))
endif

STORAGE ?= 0
ifeq ($(STORAGE),1)
export STORAGE = 1
CFLAGS += -DSTORAGE
else
export STORAGE = 0
endif

ifneq ($(shell cat .config | grep 'STORAGE'),STORAGE=$(STORAGE))
$(shell rm -rf $(BUILD_DIR))
endif

CHIPLINK ?= 0
ifeq ($(CHIPLINK),1)
export CHIPLINK = 1
override UART = 1
VFLAGS += --threads $(CPU_NUM)
endif

ifneq ($(shell cat .config | grep 'CHIPLINK'),CHIPLINK=$(CHIPLINK))
$(shell rm -rf $(BUILD_DIR) out)
endif

UART ?= 0
ifeq ($(UART),1)
export UART = 1
CSRCS  += $(SSRC_DIR)/sim/peripheral/uart/scanKbd.cpp
CFLAGS += -DUART
else
export UART = 0
CSRCS  += $(SSRC_DIR)/sim/peripheral/uart/uart.cpp
endif

ifneq ($(shell cat .config | grep 'UART'),UART=$(UART))
$(shell rm -rf $(BUILD_DIR) out)
endif

CSRCS   += $(SSRC_DIR)/sim_main.cpp $(SSRC_DIR)/sim/peripheral/ram/ram.cpp
CSRCS   += $(SSRC_DIR)/sim/peripheral/spiFlash/spiFlash.cpp
CSRCS   += $(SSRC_DIR)/sim/peripheral/storage/storage.cpp
CFLAGS  += -D$(ISA) -pthread -I$(SIM_DIR)/include
LDFLAGS += -pthread
VFLAGS  += --top TestTop --exe --timescale "1ns/1ns" -Wno-WIDTH 
VFLAGS  += -I$(SRC_DIR)/peripheral/uart16550
VFLAGS  += -I$(SRC_DIR)/tools/axi2apb/inner
VFLAGS  += -I$(SRC_DIR)/peripheral/spi/rtl -j $(CPU_NUM) -O3
VFLAGS  += -I$(SSRC_DIR)/sim/peripheral/spiFlash
VFLAGS  += -cc TestTop.v $(SRC_DIR)/peripheral/chiplink/chiplink.v $(SRC_DIR)/peripheral/chiplink/top.v

TRACE ?= 0
ifeq ($(TRACE),1)
VFLAGS += --trace-fst --trace-threads 2
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
BINFILE = $(SIM_DIR)/bin/$(BIN)-$(ISA)-nemu.bin
FLASHBINFILE = $(SIM_DIR)/bin/$(BIN)~flash-$(ISA)-nemu.bin
STORAGEBINFILE = $(SIM_DIR)/bin/$(BIN)~storage-$(ISA)-nemu.bin
ifeq ($(wildcard $(BINFILE)),)
$(shell wget $(site)/$(BIN)-$(ISA)-nemu.bin -O $(BINFILE) || rm $(BINFILE))
endif
endif

SIMBIN = $(filter-out rtthread,$(shell cd $(SIM_DIR)/bin && ls *-$(ISA)-nemu.bin | grep -oP ".*(?=-$(ISA)-nemu.bin)"))

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
	@echo FLASH=$(FLASH) >>.config
	@echo CHIPLINK=$(CHIPLINK) >>.config
	@echo STORAGE=$(STORAGE) >>.config

$(BUILD_DIR)/sim/obj_dir/VTestTop: $(BUILD_DIR)/sim/*.v $(ALL_C)
	@cd $(BUILD_DIR)/sim && \
	verilator $(VFLAGS) --build $(CSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" >/dev/null

sim: $(BUILD_DIR)/sim/obj_dir/VTestTop
	@$(BUILD_DIR)/sim/obj_dir/VTestTop $(BINFILE) $(FLASHBINFILE) $(STORAGEBINFILE)

simall: $(BUILD_DIR)/sim/obj_dir/VTestTop
	@for x in $(SIMBIN); do \
		$(BUILD_DIR)/sim/obj_dir/VTestTop $(SIM_DIR)/bin/$$x-$(ISA)-nemu.bin >/dev/null 2>&1; \
		if [ $$? -eq 0 ]; then printf "[$$x] \33[1;32mpass\33[0m\n"; \
		else                   printf "[$$x] \33[1;31mfail\33[0m\n"; fi; \
	done

.PHONY: test verilog help compile bsp reformat checkformat clean clean-all sim simall

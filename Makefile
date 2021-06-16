BUILD_DIR = ./build
ROOT_DIR = $(shell cat build.sc | grep -oP "(?<=object ).*(?= extends ScalaModule)")
SUB_DIR = $(shell cd $(ROOT_DIR); ls -d */ | tr -d / | grep -v src; cd ..)
pwd = $(shell pwd)
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

ifeq ($(TRACE),1)
override TRACE = --trace
else
override TRACE = 
endif

ifeq ($(DIFF),0)
export DIFF = 0
else
BINFILE = $(pwd)/$(ROOT_DIR)/sim/bin/$(BIN)-$(ISA)-nemu.bin
LIB_DIR = $(pwd)/$(ROOT_DIR)/sim/lib
LIBNEMU = $(pwd)/$(ROOT_DIR)/sim/lib/librv$(xlens)nemu.so
$(shell mkdir $(LIB_DIR) >>/dev/null 2>&1 | echo >>/dev/null 2>&1)
ifeq ($(wildcard $(LIBNEMU)),)
$(shell wget https://tanghaojin.site/static/librv64nemu.so -O $(LIBNEMU) || rm $(LIBNEMU))
endif
export LD_LIBRARY_PATH := $(LIB_DIR):$(LD_LIBRARY_PATH)
endif

ifneq ($(BIN),)
ifeq ($(wildcard $(BINFILE)),)
$(shell wget https://tanghaojin.site/static/$(BIN)-$(ISA)-nemu.bin -O $(BINFILE) || rm $(BINFILE))
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
	@mkdir $(BUILD_DIR) >>/dev/null 2>&1 | echo >>/dev/null 2>&1
	@mkdir $(BUILD_DIR)/sim >>/dev/null 2>&1 | echo >>/dev/null 2>&1
	ln -f $(ROOT_DIR)/sim/src/sim_main.cpp $(BUILD_DIR)/sim/sim_main.cpp
	ln -f $(ROOT_DIR)/sim/src/sim_main.hpp $(BUILD_DIR)/sim/sim_main.hpp
	ln -f $(ROOT_DIR)/sim/src/mem.txt $(BUILD_DIR)/sim/mem.txt
ifneq ($(BIN),)
	@rm -f $(BUILD_DIR)/sim/mem.txt
	@xxd -g 1 $(ROOT_DIR)/sim/bin/$(BIN)-$(ISA)-nemu.bin | \
	grep -oP "(?<=: ).*(?=  )" >$(BUILD_DIR)/sim/mem.txt
endif
	mill -i __.sim.runMain Elaborate -td $(BUILD_DIR)/sim
ifneq ($(TRACE),)

	@sed -i '$$d' $(BUILD_DIR)/sim/TestTop.v
	@echo '  initial' >>$(BUILD_DIR)/sim/TestTop.v
	@echo '  begin' >>$(BUILD_DIR)/sim/TestTop.v
	@echo '    $$dumpfile("dump.vcd");' >>$(BUILD_DIR)/sim/TestTop.v
	@echo '    $$dumpvars(0, TestTop);' >>$(BUILD_DIR)/sim/TestTop.v
	@echo '  end' >>$(BUILD_DIR)/sim/TestTop.v
	@echo 'endmodule' >>$(BUILD_DIR)/sim/TestTop.v

endif

	@cd $(BUILD_DIR)/sim && \
	verilator -cc TestTop.v --top-module TestTop --exe --build sim_main.cpp \
	-CFLAGS -D$(ISA) \
	-LDFLAGS -L$(pwd)/$(ROOT_DIR)/sim/lib \
	-LDFLAGS -lrv64nemu \
	-LDFLAGS -lreadline -Wno-WIDTH $(TRACE) >/dev/null

	@cd $(BUILD_DIR)/sim && ./obj_dir/VTestTop $(BINFILE)

ifneq ($(TRACE),)
	@vcd2fst $(BUILD_DIR)/sim/dump.vcd $(BUILD_DIR)/sim/dump.fst
endif

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

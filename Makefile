BUILD_DIR = ./build
ROOT_DIR = $(shell cat build.sc | grep -oP "(?<=object ).*(?= extends ScalaModule)")
SUB_DIR = $(shell cd $(ROOT_DIR); ls -d */ | tr -d / | grep -v src; cd ..)

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
	ln -f $(ROOT_DIR)/sim/src/sim_main.cpp $(BUILD_DIR)/sim/sim_main.cpp
	cd $(BUILD_DIR)/sim && verilator -cc TestTop.v --top-module TestTop --exe --build sim_main.cpp

.PHONY: test verilog help compile bsp reformat checkformat clean sim

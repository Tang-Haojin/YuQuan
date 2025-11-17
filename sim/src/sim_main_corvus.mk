YQ_DIR ?= $(shell realpath `pwd`/../../..)
LIB_DIR = $(YQ_DIR)/difftest/difftest/build

USER_INCLUDE_FLAGS = -I$(YQ_DIR)/sim/include
USER_MACRO_FLAGS = -DDIFFTEST
USER_LIB_FLAGS = -L$(LIB_DIR) -lrv64spike
USER_SRC_FILES = $(YQ_DIR)/sim/src/peripheral/uart/uart.cpp \
				 $(YQ_DIR)/sim/src/peripheral/sdcard/sdcard.cpp \
				 $(YQ_DIR)/sim/src/peripheral/ram/ram.cpp \
				 $(YQ_DIR)/sim/src/peripheral/spiFlash/spiFlash.cpp
MAIN_SRC = $(YQ_DIR)/sim/src/sim_main_corvus.cpp
TARGET = sim_main_corvus

export LD_LIBRARY_PATH := $(LIB_DIR):$(LD_LIBRARY_PATH)

include VCorvusTopWrapper_generated.mk

ISA := riscv64
ifneq ($(BIN),)
binFile = $(YQ_DIR)/sim/bin/$(BIN)-$(ISA)-nemu.bin
flashBinFile = $(YQ_DIR)/sim/bin/$(BIN)~flash-$(ISA)-nemu.bin
endif

sim:
ifeq ($(BIN),)
	$(error $(nobin))
endif
	@./$(TARGET) $(binFile) $(flashBinFile)

YuQuan Project
==============

Contents at a glance:

* `.gitignore` - helps Git ignore junk like generated files, build products, and temporary files.
* `build.sc` - instructs mill to build the Chisel project
* `Makefile` - rules to call mill
* `playground/src` - source directory
* `playground/src/Elaborate.scala` - wrapper file to call chisel command with the target module
* `playground/test/src` - tester directory
* `playground/sim/src` - simulation directory
* `playground/sim/Elaborate.scala` - wrapper file to call chisel command with the simulation module

## Getting Started

First, install mill by referring to the documentation [here](https://com-lihaoyi.github.io/mill).

To run all tests in this design (recommended for test-driven development):

```bash
make test
```

To generate Verilog:

```bash
make verilog
```

To run simple test:

```bash
make sim
```

To load program from bin and run test, copy `$BIN-$ISA-nemu.bin` to `playground/sim/bin/`, and run:

```bash
make BIN=$BIN [ISA=$ISA] sim
```

If `ISA` is not specified, it defaults to riscv64.

To disable difftest, run:

```bash
make BIN=$BIN DIFF=0 sim
```

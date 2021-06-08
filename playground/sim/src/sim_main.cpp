#include "obj_dir/VTestTop.h"
#include "verilated.h"

int main(int argc, char **argv, char **env) {
  Verilated::commandArgs(argc, argv);
  VTestTop *top = new VTestTop;
  top->reset = 0;
  top->clock = 0;
  for (int i = 0; i < 10; i++) {
    top->clock = !top->clock;
    top->eval();
  }
  top->reset = 1;
  for (int i = 0; i < 100; i++) {
    top->clock = !top->clock;
    top->eval();
    printf("\n");
  }
  delete top;
  return 0;
}

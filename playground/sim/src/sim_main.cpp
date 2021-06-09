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
  for (int i = 0; i < 300; i++) {
    top->clock = !top->clock;
    top->eval();
    printf("\n");
    if (top->io_exit) {
      printf("debug: ");
      if (top->io_data)
        printf("\33[1;31mHIT BAD TRAP\33[0m");
      else
        printf("\33[1;32mHIT GOOD TRAP\33[0m");
      printf(" at pc = 0x%016lx\n\n", top->io_pc - 4);
      break;
    }
  }
  delete top;
  return 0;
}

#include "obj_dir/VTestTop.h"
#include "verilated.h"

#ifdef riscv32
#define PCFMT "\33[0m at pc = 0x%08x\n\n"
#else
#define PCFMT "\33[0m at pc = 0x%016lx\n\n"
#endif

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
    if (top->io_exit == 1) {
      printf("debug: Exit after %d clock cycles.\n", i / 2);
      printf("debug: ");
      if (top->io_data)
        printf("\33[1;31mHIT BAD TRAP");
      else
        printf("\33[1;32mHIT GOOD TRAP");
      printf(PCFMT, top->io_pc - 4);
      break;
    }
    else if (top->io_exit == 2) {
      printf("debug: Exit after %d clock cycles.\n", i / 2);
      printf("debug: ");
        printf("\33[1;31mINVALID INSTRUCTION");
        printf(PCFMT, top->io_pc - 4);
        break;
    }
  }
  delete top;
  return 0;
}

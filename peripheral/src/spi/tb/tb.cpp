#include "Vtb.h"
#include "verilated.h"
#include "verilated_fst_c.h"

VerilatedContext *const contextp = new VerilatedContext;
VerilatedFstC *tfp = new VerilatedFstC;
uint64_t cycles = 0;

extern "C" void flash_init(char *img);
extern "C" void flash_read(uint64_t addr, uint64_t *data);

int main(int argc, char **argv, char **env) {
  assert(argc > 1);
  flash_init(argv[1]);
  contextp->commandArgs(argc, argv);
  Vtb *top = new Vtb;
  contextp->traceEverOn(true);
  top->trace(tfp, 0);
  tfp->open("dump.fst");

  top->rst_n = 0;
  top->clock = 0;
  top->eval();

  for (int i = 0; i < 10; i++) {
    contextp->timeInc(1);
    top->clock = !top->clock;
    top->eval();
  }

  top->rst_n = 1;

  for (; !contextp->gotFinish() && cycles < 100000; cycles++) {
    contextp->timeInc(1);
    top->clock = !top->clock;
    top->eval();
    if (cycles >= 0)
      tfp->dump(contextp->time());
  }

  tfp->close();

  delete top;
  return 0;
}

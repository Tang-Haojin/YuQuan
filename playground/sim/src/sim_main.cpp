#include "obj_dir/VTestTop.h"
#include "verilated.h"
#include "sim_main.hpp"

int main(int argc, char **argv, char **env) {
  int ret = 0;
  vaddr_t pc, nemu_pc;

  Verilated::commandArgs(argc, argv);
  Verilated::traceEverOn(true);
  VTestTop *top = new VTestTop;

  QData *gprs = &top->io_gprs_0;

  init_monitor(argc, argv);
  init_device();

  top->reset = 0;
  top->clock = 0;
  top->eval();

  for (int i = 0; i < 2; i++) {
    Verilated::timeInc(1);
    top->clock = !top->clock;
    top->eval();
  }

  top->reset = 1;
  for (int i = 0; i < 2000000; i++) {
    Verilated::timeInc(1);
    top->clock = !top->clock;
    top->eval();

    if (top->io_wbValid && top->clock) {
      pc = top->io_wbPC;
      nemu_pc = cpu.pc;
      // printf("pc=" FMT_WORD "\tnemu_pc=" FMT_WORD "\n", top->io_wbPC, cpu.pc);
      if (pc != nemu_pc) {
        printf("\33[1;31mPC Diff\33[0m\n");
        printf("pc = " FMT_WORD "\tnemu_pc=" FMT_WORD "\n", top->io_wbPC, cpu.pc);
        ret = 1;
        break;
      }
      isa_exec_once();
      if (cpu.gpr[top->io_wbRd]._64 != gprs[top->io_wbRd]) {
        printf("\33[1;31mGPR[%d] Diff\33[0m ", top->io_wbRd);
        printf("at pc = " FMT_WORD "\n", pc);
        printf("GPR[%d] = " FMT_WORD "\tnemu_GPR[%d]=" FMT_WORD "\n",
               top->io_wbRd, gprs[top->io_wbRd],
               top->io_wbRd, cpu.gpr[top->io_wbRd]._64);
        ret = 1;
        break;
      }
    }

    if (top->io_exit == 1) {
      printf("debug: Exit after %d clock cycles.\n", i / 2);
      printf("debug: ");
      if (top->io_data) {
        printf("\33[1;31mHIT BAD TRAP");
        ret = 1;
      }
      else
        printf("\33[1;32mHIT GOOD TRAP");
      printf("\33[0m at pc = " FMT_WORD "\n\n", top->io_pc - 4);
      break;
    }
    else if (top->io_exit == 2) {
      printf("debug: Exit after %d clock cycles.\n", i / 2);
      printf("debug: ");
      printf("\33[1;31mINVALID INSTRUCTION");
      printf("\33[0m at pc = " FMT_WORD "\n\n", top->io_pc - 4);
      ret = 1;
      break;
    }
  }

  delete top;

  return ret;
}

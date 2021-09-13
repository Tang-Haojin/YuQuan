#include "VTestTop.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <sim_main.hpp>

VerilatedContext *const contextp = new VerilatedContext;
#ifdef TRACE
VerilatedFstC *tfp = new VerilatedFstC;
#endif
struct termios new_settings, stored_settings;
uint64_t cycles = 0;
static bool int_sig = false;

void int_handler(int sig) {
  if (sig != SIGINT) {
    if (write(STDERR_FILENO, "Wrong signal type\n", 19)) exit(EPERM);
    else _exit(EIO);
  }
  int_sig = true;
}

void real_int_handler(void) {
  tcsetattr(0, TCSAFLUSH, &stored_settings);
  setlinebuf(stdout);
  setlinebuf(stderr);
  scan_uart(_isRunning) = false;
#ifdef TRACE
  tfp->close();
#endif
  printf("\ndebug: Exit after %ld clock cycles.\n", cycles / 2);
  exit(0);
}

int main(int argc, char **argv, char **env) {
  VTestTop *top = new VTestTop;

#ifdef DIFFTEST
  uint64_t *ram_param =
#endif
  ram_init(argv[1]);

#ifdef FLASH
  flash_init(argv[2]);
#endif

#ifdef STORAGE
  storage_init(argv[3]);
#endif

#ifdef DIFFTEST
  vaddr_t pc, spike_pc;
  {
    size_t tmp[33] = {};
    tmp[32] = 0x80000000UL;
    difftest_init(0);
    difftest_regcpy(tmp, DIFFTEST_TO_REF);
    difftest_memcpy(0x80000000UL, (void *)(ram_param[0]), ram_param[1], DIFFTEST_TO_REF);
  }
  QData *gprs = &top->io_gprs_0;
#endif

  setbuf(stdout, NULL);
  setbuf(stderr, NULL);
  signal(SIGINT, int_handler);

  tcgetattr(0, &stored_settings);
  new_settings = stored_settings;
  new_settings.c_lflag &= ~ECHOFLAGS;
  tcsetattr(0, TCSAFLUSH, &new_settings);
  contextp->commandArgs(argc, argv);
  
  int ret = 0;
  scan_uart(_init)();

#ifdef TRACE
  contextp->traceEverOn(true);
  top->trace(tfp, 0);
  tfp->open("dump.fst");
#endif
  top->reset = 1;
  top->clock = 0;
  top->eval();

  for (int i = 0; i < 50; i++) {
    contextp->timeInc(1);
    top->clock = !top->clock;
    top->eval();
  }

  top->reset = 0;
  for (;!contextp->gotFinish();cycles++) {
    contextp->timeInc(1);
    top->clock = !top->clock;
    top->eval();
#ifdef TRACE
    if (cycles >= 0)
      tfp->dump(contextp->time());
#endif

#ifdef DIFFTEST
    if (top->io_wbValid && top->clock) {
      pc = top->io_wbPC;
      spike_pc = diff_context.pc[0];
      if (pc != spike_pc) {
        printf("debug: Exit after %ld clock cycles.\n", cycles / 2);
        printf("debug: ");
        printf("\33[1;31mPC Diff\33[0m\n");
        printf("pc = " FMT_WORD "\tspike_pc=" FMT_WORD "\n", pc, spike_pc);
        ret = 1;
        break;
      }
      difftest_exec(1);
      if (diff_context.gpr[top->io_wbRd] != gprs[top->io_wbRd]) {
        printf("debug: Exit after %ld clock cycles.\n", cycles / 2);
        printf("debug: ");
        printf("\33[1;31mGPR[%d] Diff\33[0m ", top->io_wbRd);
        printf("at pc = " FMT_WORD "\n", pc);
        printf("GPR[%d] = " FMT_WORD "\tspike_GPR[%d]=" FMT_WORD "\n",
               top->io_wbRd, gprs[top->io_wbRd],
               top->io_wbRd, diff_context.gpr[top->io_wbRd]);
        ret = 1;
        break;
      }
    }
#endif

    if (top->io_exit == 1) {
      printf("debug: Exit after %ld clock cycles.\n", cycles / 2);
      printf("debug: ");
      if (top->io_data) {
        printf("\33[1;31mHIT BAD TRAP");
        ret = 1;
      }
      else
        printf("\33[1;32mHIT GOOD TRAP");
      printf("\33[0m at pc = " FMT_WORD "\n\n", top->io_wbPC - 4);
      break;
    }
    else if (top->io_exit == 2) {
      printf("debug: Exit after %ld clock cycles.\n", cycles / 2);
      printf("debug: ");
      printf("\33[1;31mINVALID INSTRUCTION");
      printf("\33[0m at pc = " FMT_WORD "\n\n", top->io_wbPC - 4);
      ret = 1;
      break;
    }
    if (int_sig) real_int_handler();
  }

  scan_uart(_isRunning) = false;
  delete top;
  tcsetattr(0, TCSAFLUSH, &stored_settings);
  setlinebuf(stdout);
  setlinebuf(stderr);
#ifdef TRACE
  tfp->close();
#endif
  return ret;
}

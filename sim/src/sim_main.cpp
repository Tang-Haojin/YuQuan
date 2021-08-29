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
  ram_init(argv[1]);

#ifdef FLASH
  flash_init(argv[2]);
#endif

#ifdef STORAGE
  storage_init(argv[3]);
#endif

  VTestTop *top = new VTestTop;
#ifdef TRACE
  contextp->traceEverOn(true);
  top->trace(tfp, 0);
  tfp->open("dump.fst");
#endif

#ifdef DIFFTEST
  vaddr_t pc, nemu_pc;
  init_monitor(argc, argv);
  init_device();
  QData *gprs = &top->io_gprs_0;
#endif

  top->reset = 0;
  top->clock = 0;
  top->eval();

  for (int i = 0; i < 50; i++) {
    contextp->timeInc(1);
    top->clock = !top->clock;
    top->eval();
  }

  top->reset = 1;
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
      nemu_pc = cpu.pc;
      if (pc != nemu_pc) {
        printf("debug: Exit after %ld clock cycles.\n", cycles / 2);
        printf("debug: ");
        printf("\33[1;31mPC Diff\33[0m\n");
        printf("pc = " FMT_WORD "\tnemu_pc=" FMT_WORD "\n", top->io_wbPC, cpu.pc);
        ret = 1;
        break;
      }
      isa_exec_once();
      if (cpu.gpr[top->io_wbRd]._64 != gprs[top->io_wbRd]) {
        printf("debug: Exit after %ld clock cycles.\n", cycles / 2);
        printf("debug: ");
        printf("\33[1;31mGPR[%d] Diff\33[0m ", top->io_wbRd);
        printf("at pc = " FMT_WORD "\n", pc);
        printf("GPR[%d] = " FMT_WORD "\tnemu_GPR[%d]=" FMT_WORD "\n",
               top->io_wbRd, gprs[top->io_wbRd],
               top->io_wbRd, cpu.gpr[top->io_wbRd]._64);
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

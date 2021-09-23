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
    if (write(STDERR_FILENO, "Wrong signal type\n", 19)) _exit(EPERM);
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
  printf("\n" DEBUG "Exit after %ld clock cycles.\n", cycles / 2);
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
    size_t tmp[50] = {};
    difftest_init(0);
    difftest_regcpy(tmp, DIFFTEST_TO_DUT);
    tmp[32] = 0x80000000UL;
    difftest_regcpy(tmp, DIFFTEST_TO_REF);
    difftest_memcpy(0x80000000UL, (void *)(ram_param[0]), ram_param[1], DIFFTEST_TO_REF);
  }
  QData *gprs = &top->io_gprs_0;
  char name[15] = {};
  size_t cpu_reg, diff_reg;
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
    if (cycles >= 37300000)
      tfp->dump(contextp->time());
#endif

#ifdef DIFFTEST
    if (top->io_wbValid && top->clock) {
      pc = top->io_wbPC;
#ifndef TRACE
      assert(pc > 0x7ffffff8UL);
#endif
      spike_pc = diff_gpr_pc.pc[0];
      if (pc != spike_pc) {
        strcpy(name, "pc");
        cpu_reg = pc;
        diff_reg = spike_pc;
        goto reg_diff;
      }
      if (!top->io_wbIntr && !top->io_wbClint && !top->io_exit && top->io_wbRcsr != 0xBFF &&
          top->io_wbRcsr != 0xBFE && top->io_wbRcsr != 0x344 && top->io_wbRcsr != 0x301 &&
          !in_pmpaddr(top->io_wbRcsr) && top->io_wbRcsr != 0xC01 && !top->io_wbMMIO) {
        difftest_exec(1);
        size_t diff_regs[50];
        difftest_regcpy(diff_regs, DIFFTEST_TO_DUT);
        add_diff(mcause);
        add_diff(scause);
        add_diff(mepc);
        add_diff(sepc);
        add_diff(mstatus);
        add_diff(mtvec);
        add_diff(stvec);
        add_diff(mie);
        add_diff(mscratch);
        add_diff(priv);
        for (int i = 0; i < 32; i++) if (diff_regs[i] != gprs[i]) {
          char tmp[10];
          sprintf(tmp, "GPR[%d]", i);
          strcpy(name, tmp);
          cpu_reg = gprs[i];
          diff_reg = diff_regs[i];
          goto reg_diff;
        }
      } else {
        size_t tmp[50];
        difftest_regcpy(tmp, DIFFTEST_TO_DUT);
        memcpy(tmp, gprs, 32 * sizeof(size_t));
        tmp[mstatus] = top->io_mstatus;
        tmp[mepc] = top->io_mepc;
        tmp[sepc] = top->io_sepc;
        tmp[mtvec] = top->io_mtvec;
        tmp[stvec] = top->io_stvec;
        tmp[mcause] = top->io_mcause;
        tmp[scause] = top->io_scause;
        tmp[mie] = top->io_mie;
        tmp[mscratch] = top->io_mscratch;
        if (top->io_wbIntr) tmp[priv] = top->io_priv;
        tmp[32] = top->io_wbIntr ? (top->io_priv == 0b11 ? tmp[mtvec] : tmp[stvec]) : pc + 4;
        difftest_regcpy(tmp, DIFFTEST_TO_REF);
      }
    }
#endif

    if (top->io_exit == 1) {
      printf(DEBUG "Exit after %ld clock cycles.\n", cycles / 2);
      printf(DEBUG);
      if (top->io_data) {
        printf("\33[1;31mHIT BAD TRAP");
        ret = 1;
      }
      else printf("\33[1;32mHIT GOOD TRAP");
      printf("\33[0m at pc = " FMT_WORD "\n\n", top->io_wbPC - 4);
      break;
    }
    else if (top->io_exit == 2) {
      printf(DEBUG "Exit after %ld clock cycles.\n", cycles / 2);
      printf(DEBUG "\33[1;31mINVALID INSTRUCTION");
      printf("\33[0m at pc = " FMT_WORD "\n\n", top->io_wbPC - 4);
      ret = 1;
      break;
    }
    if (int_sig) real_int_handler();
#ifdef DIFFTEST
    continue;
  reg_diff:
    std::cout << DEBUG "Exit after " << cycles / 2 << " clock cycles.\n";
    std::cout << DEBUG "\33[1;31m" << name << " Diff\33[0m ";
    printf("at pc = " FMT_WORD "\n" DEBUG, pc);
    printf("%s = " FMT_WORD "\tspike_%s = " FMT_WORD "\n", name, cpu_reg, name, diff_reg);
    ret = 1;
    break;
#endif
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

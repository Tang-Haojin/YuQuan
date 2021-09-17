#ifndef _SIM_MAIN_HPP
#define _SIM_MAIN_HPP

#include <unistd.h>
#include <stdint.h>
#include <signal.h>
#include <errno.h>
#include <termio.h>
#include <iostream>
#include <iomanip>

#define DEBUG "\33[1;33m[debug]\33[0m "

#define concat_temp(x, y) x##y
#define concat(x, y) concat_temp(x, y)
#define MAP(c, f) c(f)

#define FMT_WORD "0x%016lx"
typedef uint64_t word_t;
typedef int64_t sword_t;

#define PCFMT "\33[0m at pc = " FMT_WORD "\n\n"

typedef word_t rtlreg_t;
typedef word_t vaddr_t;
typedef uint32_t paddr_t;
typedef uint16_t ioaddr_t;

#ifdef UART
 #define SCAN_OR_UART scan 
#else
 #define SCAN_OR_UART uart
#endif

#define scan_uart(x) concat(SCAN_OR_UART, x)

extern "C" {

#ifdef DIFFTEST

struct diff_gpr_pc_p {
  volatile const size_t *volatile gpr;
  volatile const size_t *volatile pc;
};

extern struct diff_gpr_pc_p diff_gpr_pc;

enum pc_csr { pc = 32, mstatus, mepc, sepc, mtvec, stvec, mcause, scause, mie, mscratch, priv };

enum { DIFFTEST_TO_DUT, DIFFTEST_TO_REF };
void difftest_init(int port);
void difftest_exec(uint64_t n);
void difftest_regcpy(void *dut, bool direction);
void difftest_memcpy(paddr_t addr, void *buf, size_t n, bool direction);

#define in_pmpaddr(x) ((x) >= 0x3B0 && (x) <= 0x3BF)

#define add_diff(reg)                            \
  if (diff_regs[pc_csr::reg] != top->io_##reg) { \
    strcpy(name, #reg);                          \
    cpu_reg = top->io_##reg;                     \
    diff_reg = diff_regs[pc_csr::reg];           \
    goto reg_diff;                               \
  }

#endif


void scan_uart(_init)(void);
uint64_t *ram_init(char *img);
extern bool scan_uart(_isRunning);
void flash_init(char *img);
void storage_init(char *img);

}

#define ECHOFLAGS (ECHO | ECHOE | ECHOK | ECHONL)

#endif

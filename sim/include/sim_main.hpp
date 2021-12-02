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

#define to_string_temp(x) #x
#define to_string(x) to_string_temp(x)
#define concat_temp(x, y) x##y
#define concat(x, y) concat_temp(x, y)
#define MAP(c, f) c(f)

#ifndef _countof
#define _countof(x) (sizeof(x) / sizeof((x)[0]))
#endif

#define FMT_WORD "0x%016lx"
typedef uint64_t word_t;
typedef int64_t sword_t;

#define PCFMT "\33[0m at pc = " FMT_WORD "\n\n"

typedef word_t rtlreg_t;
typedef word_t vaddr_t;
typedef uint32_t paddr_t;
typedef uint16_t ioaddr_t;

// ramdisk
#define BSIZE  1024  // block size
#define FSSIZE 1000  // size of file system in blocks

#define PMEM_SIZE (128 * 1024 * 1024 + BSIZE * FSSIZE)

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

enum pc_csr { pc = 32, mstatus, mepc, sepc, mtvec, stvec, mcause, scause, mtval, stval, mie, mscratch, priv };

enum { DIFFTEST_TO_DUT, DIFFTEST_TO_REF };
void difftest_init(int port);
void difftest_exec(uint64_t n);
void difftest_regcpy(void *dut, bool direction);
void difftest_memcpy(paddr_t addr, void *buf, size_t n, bool direction);

#define add_diff(reg)                            \
  if (diff_regs[pc_csr::reg] != top->io_##reg) { \
    strcpy(name, #reg);                          \
    cpu_reg = top->io_##reg;                     \
    diff_reg = diff_regs[pc_csr::reg];           \
    goto reg_diff;                               \
  }

#define print_csr(csr) printf("%s = " FMT_WORD "\tspike_%s = " FMT_WORD "\n", #csr, (uint64_t)top->io_##csr, #csr, (uint64_t)diff_regs[csr])

#endif


void scan_uart(_init)(void);
void *ram_init(char *img);
void sdcard_init(char *img);
extern bool scan_uart(_isRunning);
void flash_init(char *img);
void storage_init(char *img);
void command_init(const char command[]);

}

#define ECHOFLAGS (ECHO | ECHOE | ECHOK | ECHONL)

#endif

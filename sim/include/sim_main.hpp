#ifndef _SIM_MAIN_HPP
#define _SIM_MAIN_HPP

#include <unistd.h>
#include <stdint.h>
#include <signal.h>
#include <errno.h>
#include <termio.h>

#define concat_temp(x, y) x##y
#define concat(x, y) concat_temp(x, y)

#ifdef riscv32
#define FMT_WORD "0x%08x"
typedef uint32_t word_t;
typedef int32_t sword_t;
#else
#define FMT_WORD "0x%016lx"
typedef uint64_t word_t;
typedef int64_t sword_t;
#endif

#define PCFMT "\33[0m at pc = " FMT_WORD "\n\n"

typedef word_t rtlreg_t;
typedef word_t vaddr_t;
typedef uint32_t paddr_t;
typedef uint16_t ioaddr_t;

#ifdef riscv32
typedef struct {
  struct {
    rtlreg_t _32;
  } gpr[32];

  vaddr_t pc;
  union {
    struct {
      rtlreg_t _32;
    } scsr[6];
    struct {
      rtlreg_t sepc;
      rtlreg_t stvec;
      rtlreg_t scause;
      rtlreg_t sscratch;
      rtlreg_t stval;
      rtlreg_t sstatus;
    };
  };
} CPU_state;

#else

typedef struct {
  struct {
    rtlreg_t _64;
  } gpr[32];

  vaddr_t pc;
  union {
    struct {
      rtlreg_t _64;
    } scsr[7];
    struct {
      rtlreg_t sepc;
      rtlreg_t stvec;
      rtlreg_t scause;
      rtlreg_t sscratch;
      rtlreg_t stval;
      union {
        rtlreg_t sstatus;
        struct {
          rtlreg_t UIE : 1;
          rtlreg_t SIE : 1;
          rtlreg_t : 2;
          rtlreg_t UPIE : 1;
          rtlreg_t SPIE : 1;
          rtlreg_t : 2;
          rtlreg_t SPP : 1;
          rtlreg_t : 4;
          rtlreg_t FS1_0 : 2;
          rtlreg_t XS1_0 : 2;
          rtlreg_t : 1;
          rtlreg_t SUM : 1;
          rtlreg_t MXR : 1;
          rtlreg_t : 43;
          rtlreg_t SD : 1;
        } sstatus_bits;
      };
      rtlreg_t satp;
    };
  };
  bool INTR;
} CPU_state;

#endif

#ifdef UART
 #define SCAN_OR_UART scan 
#else
 #define SCAN_OR_UART uart
#endif

#define scan_uart(x) concat(SCAN_OR_UART, x)

extern "C" {

#ifdef DIFFTEST

struct diff_context_p {
  volatile const size_t *volatile gpr;
  volatile const size_t *pc;
};

extern struct diff_context_p diff_context;

enum { DIFFTEST_TO_DUT, DIFFTEST_TO_REF };
void difftest_init(int port);
void difftest_exec(uint64_t n);
void difftest_regcpy(void *dut, bool direction);
void difftest_memcpy(paddr_t addr, void *buf, size_t n, bool direction);

#define in_pmpaddr(x) ((x) >= 0x3B0 && (x) <= 0x3BF)

#endif


void scan_uart(_init)(void);
uint64_t *ram_init(char *img);
extern bool scan_uart(_isRunning);
void flash_init(char *img);
void storage_init(char *img);

}

#define ECHOFLAGS (ECHO | ECHOE | ECHOK | ECHONL)

#endif

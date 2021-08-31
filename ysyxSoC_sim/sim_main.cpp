#include "VSimTop.h"
#include "verilated.h"
#include "verilated_fst_c.h"

#include <unistd.h>
#include <stdint.h>
#include <signal.h>
#include <errno.h>
#include <termio.h>

#define ECHOFLAGS (ECHO | ECHOE | ECHOK | ECHONL)

extern "C" void flash_init(char *img);

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
  contextp->commandArgs(argc, argv);
  
  int ret = 0;
  flash_init(argv[1]);

  VSimTop *top = new VSimTop;
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
    if (int_sig) real_int_handler();
  }

  delete top;
#ifdef TRACE
  tfp->close();
#endif
  return ret;
}
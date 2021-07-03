#include <termio.h>
#include <stdio.h>
#include <pthread.h>
#include <svdpi.h>

#define FIFO_SIZE 1024
static char fifo[FIFO_SIZE] = {0};
static short head = 0, tail = 0;
bool uart_isRunning = false;
static bool divisor_latch = false;
static bool receive_interrupt = false;
static char scratch = 0;

enum {
  Receive_Holding  = 0b000,
  Interrupt_Status = 0b010,
  Line_Status      = 0b101,
  Modem_Status     = 0b110,
  Scratchpad_Read  = 0b111
}; // READ MODE

enum {
  Transmit_Holding = 0b000,
  Interrupt_Enable = 0b001,
  FIFO_control     = 0b010,
  Line_Control     = 0b011,
  Modem_Control    = 0b100,
  Scratchpad_Write = 0b111
}; // WRITE MODE

pthread_t thread_in;
pthread_mutex_t mutex_fifo_opt = PTHREAD_MUTEX_INITIALIZER;

static inline int scanKeyboard() {
  int in;
  struct termios new_settings;
  struct termios stored_settings;
  tcgetattr(0, &stored_settings);
  new_settings = stored_settings;
  new_settings.c_lflag &= (~ICANON);
  new_settings.c_cc[VTIME] = 0;
  tcgetattr(0, &stored_settings);
  new_settings.c_cc[VMIN] = 1;
  tcsetattr(0, TCSANOW, &new_settings);
  in = getchar();
  tcsetattr(0, TCSANOW, &stored_settings);
  return in;
}

extern "C" void uart_read(char addr, char *ch) {
  if (!ch) return;
  switch (addr) {
    case Receive_Holding:
      pthread_mutex_lock(&mutex_fifo_opt);
      if (head != tail) {
        *ch = fifo[head];
        head = (head + 1) % FIFO_SIZE;
      } else *ch = -1;
      pthread_mutex_unlock(&mutex_fifo_opt);
      break;
    case Interrupt_Status:
      *ch = receive_interrupt ? ((head == tail) ? 1 : 1 << 2) : 1; break;
    case Line_Status:
      *ch = 0b0110000 | (head != tail); break;
    case Modem_Status:
      *ch = 0; break;
    case Scratchpad_Read:
      *ch = scratch; break;
    default:
      *ch = 0; break;
  }
}

extern "C" void uart_write(char addr, char data) {
  switch (addr) {
    case Transmit_Holding:
      if (!divisor_latch)
        putchar(data);
      break;
    case Interrupt_Enable:
      if (!divisor_latch) receive_interrupt = (data & 1U); break;
    case Line_Control:
      divisor_latch = ((data & (1 << 7)) != 0); break;
    case Scratchpad_Write:
      scratch = data; break;
  }
}

static void fifo_in() {
  uart_isRunning = true;
  while (uart_isRunning) {
    char key = scanKeyboard();
    pthread_mutex_lock(&mutex_fifo_opt);
    short n = (tail + 1) % FIFO_SIZE;
    if (n != head) {
      fifo[tail] = key;
      tail = n;
    }
    pthread_mutex_unlock(&mutex_fifo_opt);
  }
}

extern "C" void uart_init() {
  pthread_create(&thread_in, NULL, (void *(*)(void *))fifo_in, NULL);
}

extern "C" void uart_reset() {
  pthread_mutex_lock(&mutex_fifo_opt);
  head = tail = 0;
  scratch = 0;
  divisor_latch = false;
  pthread_mutex_unlock(&mutex_fifo_opt);
}

extern "C" void uart_int(svBit *interrupt) {
  if (interrupt) *interrupt = (head != tail);
}

#include <termio.h>
#include <stdio.h>
#include <pthread.h>

#define FIFO_SIZE 1024
static char fifo[FIFO_SIZE] = {0};
static short head = 0, tail = 0;
bool uart_isRunning = false;

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

extern "C" void uart_getc(char *ch) {
  printf("uart_getc\n");
  pthread_mutex_lock(&mutex_fifo_opt);
  if (head != tail) {
    *ch = fifo[head];
    head = (head + 1) % FIFO_SIZE;
  } else *ch = -1;
  pthread_mutex_unlock(&mutex_fifo_opt);
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
  pthread_mutex_unlock(&mutex_fifo_opt);
}

#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <assert.h>
#include <svdpi.h>

#define Assert(cond, ...) \
  do { \
    if (!(cond)) { \
      fflush(stdout); \
      fprintf(stderr, "\33[1;31m"); \
      fprintf(stderr, __VA_ARGS__); \
      fprintf(stderr, "\33[0m\n"); \
      assert(cond); \
    } \
  } while (0)

#define PMEM_SIZE (256 * 1024 * 1024)

#define PAGE_SIZE 4096
#define PAGE_MASK (PAGE_SIZE - 1)
#define PG_ALIGN __attribute((aligned(PAGE_SIZE)))

static inline bool in_pmem(uint64_t addr) {
  return (addr < PMEM_SIZE);
}

static uint8_t pmem[PMEM_SIZE] PG_ALIGN = {};

extern "C" uint64_t flash_read(uint64_t addr) {
  Assert(in_pmem(addr), "Flash address 0x%lx out of bound", addr);
  return in_pmem(addr) ? *(uint64_t *)(pmem + addr) : 0xBB;
}

extern "C" void flash_init(char *img) {
  FILE *fp = fopen(img, "rb");
  Assert(fp, "Can not open '%s'", img);

  fseek(fp, 0, SEEK_END);
  uint64_t size = ftell(fp);

  fseek(fp, 0, SEEK_SET);
  assert(fread(pmem, size, 1, fp) == 1);

  fclose(fp);
}

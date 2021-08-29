#include <stdio.h>
#include <stdint.h>
#include <svdpi.h>
#include <debug.hpp>

#define PMEM_SIZE (128 * 1024 * 1024)

#define PAGE_SIZE 4096
#define PAGE_MASK (PAGE_SIZE - 1)
#define PG_ALIGN __attribute((aligned(PAGE_SIZE)))

static inline bool in_pmem(uint64_t addr) {
  return (addr < PMEM_SIZE);
}

static uint8_t pmem[PMEM_SIZE] PG_ALIGN = {};

extern "C" uint64_t ram_read(uint64_t addr) {
  return in_pmem(addr) ? *(uint64_t *)(pmem + addr) : 0xBB;
}

extern "C" void ram_write(uint64_t addr, uint64_t data, uint8_t mask) {
  uint8_t *p = &pmem[addr];
  if (in_pmem(addr))
    for (int i = 0; i < 8; i++) {
      if (mask & 1) *p = data;
      p++;
      data >>= 8;
      mask >>= 1;
    }
}

extern "C" void ram_init(char *img) {
  FILE *fp = fopen(img, "rb");
  Assert(fp, "Can not open '%s'", img);

  fseek(fp, 0, SEEK_END);
  uint64_t size = ftell(fp);

  fseek(fp, 0, SEEK_SET);
  assert(fread(pmem, size, 1, fp) == 1);

  fclose(fp);
}

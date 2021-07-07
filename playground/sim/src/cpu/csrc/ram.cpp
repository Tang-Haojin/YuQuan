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

extern "C" void ram_read(uint64_t addr, uint64_t *data) {
  if (!data) return;
  *data = in_pmem(addr) ? *(uint64_t *)(pmem + addr) : 0xBB;
}

extern "C" void ram_write(uint64_t addr, uint64_t data, uint8_t mask) {
  void *p = &pmem[addr];
  if (in_pmem(addr))
    switch (mask) {
      case 0b00000001U: *(uint8_t  *)p = data; break;
      case 0b00000011U: *(uint16_t *)p = data; break;
      case 0b00001111U: *(uint32_t *)p = data; break;
      case 0b11111111U: *(uint64_t *)p = data; break;
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

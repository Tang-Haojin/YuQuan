#include <stdio.h>
#include <string>
#include <stdint.h>
#include <svdpi.h>
#include <debug.hpp>
#include <mmc.hpp>

// http://www.files.e-shop.co.il/pdastore/Tech-mmc-samsung/SEC%20MMC%20SPEC%20ver09.pdf

// see page 26 of the manual above
#define MEMORY_SIZE (16ull * 1024 * 1024 * 1024)  // 16GB
#define READ_BL_LEN 15
#define BLOCK_LEN (1 << READ_BL_LEN)
#define NR_BLOCK (MEMORY_SIZE / BLOCK_LEN)
#define C_SIZE_MULT 7  // only 3 bits
#define MULT (1 << (C_SIZE_MULT + 2))
#define C_SIZE (NR_BLOCK / MULT - 1)

// This is a simple hardware implementation of linux/drivers/mmc/host/bcm2835.c
// No DMA and IRQ is supported, so the driver must be modified to start PIO
// right after sending the actual read/write commands.

enum {
  SDCMD, SDARG, SDTOUT, SDCDIV,
  SDRSP0, SDRSP1, SDRSP2, SDRSP3,
  SDHSTS, __PAD0, __PAD1, __PAD2,
  SDVDD, SDEDM, SDHCFG, SDHBCT,
  SDDATA, __PAD10, __PAD11, __PAD12,
  SDHBLC
};

static FILE *fp = NULL;
static uint32_t base[0x80] = {};
static uint32_t blkcnt = 0;
static long blk_addr = 0;
static uint32_t addr = 0;
static bool write_cmd = 0;
static bool read_ext_csd = false;

static void prepare_rw(int is_write) {
  blk_addr = base[SDARG];
  addr = 0;
  if (fp) fseek(fp, blk_addr << 9, SEEK_SET);
  write_cmd = is_write;
}

static void sdcard_handle_cmd(int cmd) {
  switch (cmd) {
    case MMC_GO_IDLE_STATE: break;
    case MMC_SEND_OP_COND: base[SDRSP0] = 0x80ff8000; break;
    case MMC_ALL_SEND_CID:
      base[SDRSP0] = 0x00000001;
      base[SDRSP1] = 0x00000000;
      base[SDRSP2] = 0x00000000;
      base[SDRSP3] = 0x15000000;
      break;
    case 52: // ???
      break;
    case MMC_SEND_CSD:
      base[SDRSP0] = 0x92404001;
      base[SDRSP1] = 0x124b97e3 | ((C_SIZE & 0x3) << 30);
      base[SDRSP2] = 0x0f508000 | (C_SIZE >> 2) | (READ_BL_LEN << 16);
      base[SDRSP3] = 0x9026012a;
      break;
    case MMC_SEND_EXT_CSD: read_ext_csd = true; addr = 0; break;
    case MMC_SLEEP_AWAKE: break;
    case MMC_APP_CMD: break;
    case MMC_SET_RELATIVE_ADDR: break;
    case MMC_SELECT_CARD: break;
    case MMC_SET_BLOCK_COUNT: blkcnt = base[SDARG] & 0xffff; break;
    case MMC_READ_MULTIPLE_BLOCK: prepare_rw(false); break;
    case MMC_WRITE_MULTIPLE_BLOCK: prepare_rw(true); break;
    case MMC_SEND_STATUS: base[SDRSP0] = base[SDRSP1] = base[SDRSP2] = base[SDRSP3] = 0; break;
    case MMC_STOP_TRANSMISSION: break;
    default:
      printf("unhandled command = %d", cmd);
  }
}

static void sdcard_io_handler(uint32_t offset, int len, bool is_write) {
  int idx = offset >> 2;
  switch (idx) {
    case SDCMD: sdcard_handle_cmd(base[SDCMD] & 0x3f); break;
    case SDARG:
    case SDRSP0:
    case SDRSP1:
    case SDRSP2:
    case SDRSP3:
      break;
    case SDDATA:
       if (read_ext_csd) {
         // See section 8.1 JEDEC Standard JED84-A441
         uint32_t data;
         switch (addr) {
           case 192: data = 2; break; // EXT_CSD_REV
           case 212: data = MEMORY_SIZE / 512; break;
           default: data = 0;
         }
         base[SDDATA] = data;
         if (addr == 512 - 4) read_ext_csd = false;
       } else if (fp) {
         __attribute__((unused)) int ret;
         if (!write_cmd) { ret = fread(&base[SDDATA], 4, 1, fp); }
         else { ret = fwrite(&base[SDDATA], 4, 1, fp); }
       }else{
         assert(0);
       }
       addr += 4;
       break;
    default:
      printf("offset = 0x%x(idx = %d), is_write = %d, data = 0x%x", offset, idx, is_write, base[idx]);
  }
}

extern "C" void sdcard_read(uint64_t addr, uint32_t *rdata) {
  sdcard_io_handler(addr, 4, 0);
  *rdata = base[addr >> 2];
}

extern "C" void sdcard_write(uint64_t addr, uint32_t wdata) {
  base[addr >> 2] = wdata;
  sdcard_io_handler(addr, 4, 1);
}

extern "C" void sdcard_init(char *img) {
  std::string sdcard = img;
  sdcard.replace(sdcard.find(".bin"), 4, "-sdcard.img");
  if (fp = fopen(sdcard.c_str(), "rb"))
    printf(DEBUG "found sdcard %s\n", sdcard.c_str());
}

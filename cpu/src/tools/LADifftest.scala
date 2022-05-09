package cpu.tools.difftest

import chisel3._

class DifftestInstrCommit extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock          = Clock()
    val coreid         = UInt(8.W)
    val index          = UInt(8.W)
    val valid          = Bool()
    val pc             = UInt(64.W)
    val instr          = UInt(32.W)
    val skip           = Bool()
    val is_TLBFILL     = Bool()
    val TLBFILL_index  = UInt(5.W)
    val is_CNTinst     = Bool()
    val timer_64_value = UInt(64.W)
    val wen            = Bool()
    val wdest          = UInt(8.W)
    val wdata          = UInt(64.W)
    val csr_rstat      = Bool()
    val csr_data       = UInt(32.W)
  }))
}

class DifftestExcpEvent extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock         = Clock()
    val coreid        = UInt(8.W)
    val excp_valid    = Bool()
    val eret          = Bool()
    val intrNo        = UInt(32.W)
    val cause         = UInt(32.W)
    val exceptionPC   = UInt(64.W)
    val exceptionInst = UInt(32.W)
  }))
}

class DifftesTrapEvent extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock    = Clock()
    val coreid   = UInt(8.W)
    val valid    = Bool()
    val code     = UInt(3.W)
    val pc       = UInt(64.W)
    val cycleCnt = UInt(64.W)
    val instrCnt = UInt(64.W)
  }))
}

class DifftestStoreEvent extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock      = Clock()
    val coreid     = UInt(8.W)
    val index      = UInt(8.W)
    val valid      = UInt(8.W)
    val storePAddr = UInt(64.W)
    val storeVAddr = UInt(64.W)
    val storeData  = UInt(64.W)
  }))
}

class DifftestLoadEvent extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock  = Clock()
    val coreid = UInt(8.W)
    val index  = UInt(8.W)
    val valid  = UInt(8.W)
    val paddr  = UInt(64.W)
    val vaddr  = UInt(64.W)
  }))
}

class DifftestCSRRegState extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock     = Clock()
    val coreid    = UInt(8.W)
    val crmd      = UInt(64.W)
    val prmd      = UInt(64.W)
    val euen      = UInt(64.W)
    val ecfg      = UInt(64.W)
    val estat     = UInt(64.W)
    val era       = UInt(64.W)
    val badv      = UInt(64.W)
    val eentry    = UInt(64.W)
    val tlbidx    = UInt(64.W)
    val tlbehi    = UInt(64.W)
    val tlbelo0   = UInt(64.W)
    val tlbelo1   = UInt(64.W)
    val asid      = UInt(64.W)
    val pgdl      = UInt(64.W)
    val pgdh      = UInt(64.W)
    val save0     = UInt(64.W)
    val save1     = UInt(64.W)
    val save2     = UInt(64.W)
    val save3     = UInt(64.W)
    val tid       = UInt(64.W)
    val tcfg      = UInt(64.W)
    val tval      = UInt(64.W)
    val ticlr     = UInt(64.W)
    val llbctl    = UInt(64.W)
    val tlbrentry = UInt(64.W)
    val dmw0      = UInt(64.W)
    val dmw1      = UInt(64.W)
  }))
}

class DifftestGRegState extends BlackBox {
  val io = IO(Input(new Bundle {
    val clock  = Clock()
    val coreid = UInt(8.W)
    val gpr_0  = UInt(64.W)
    val gpr_1  = UInt(64.W)
    val gpr_2  = UInt(64.W)
    val gpr_3  = UInt(64.W)
    val gpr_4  = UInt(64.W)
    val gpr_5  = UInt(64.W)
    val gpr_6  = UInt(64.W)
    val gpr_7  = UInt(64.W)
    val gpr_8  = UInt(64.W)
    val gpr_9  = UInt(64.W)
    val gpr_10 = UInt(64.W)
    val gpr_11 = UInt(64.W)
    val gpr_12 = UInt(64.W)
    val gpr_13 = UInt(64.W)
    val gpr_14 = UInt(64.W)
    val gpr_15 = UInt(64.W)
    val gpr_16 = UInt(64.W)
    val gpr_17 = UInt(64.W)
    val gpr_18 = UInt(64.W)
    val gpr_19 = UInt(64.W)
    val gpr_20 = UInt(64.W)
    val gpr_21 = UInt(64.W)
    val gpr_22 = UInt(64.W)
    val gpr_23 = UInt(64.W)
    val gpr_24 = UInt(64.W)
    val gpr_25 = UInt(64.W)
    val gpr_26 = UInt(64.W)
    val gpr_27 = UInt(64.W)
    val gpr_28 = UInt(64.W)
    val gpr_29 = UInt(64.W)
    val gpr_30 = UInt(64.W)
    val gpr_31 = UInt(64.W)
  }))
}

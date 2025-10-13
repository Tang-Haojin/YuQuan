package cpu.top

import chipsalliance.rocketchip.config.Parameters

object Elaborate extends App {
  implicit private val p: Parameters = Parameters.empty.alterPartial({ case cpu.GEN_NAME =>
    if (args.contains("zmb")) "zmb"
    else if (args.contains("lxb")) "lxb"
    else "ysyx"
  }).alter(cpu.YQConfig()).alter(cpu.cache.CacheConfig.f)

  (new circt.stage.ChiselStage).execute(
    Array("--target", "systemverilog", "--split-verilog") ++ args,
    Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new cpu.CPU)) :+
    circt.stage.FirtoolOption("--disable-all-randomization") :+
    circt.stage.FirtoolOption("--default-layer-specialization=enable")
  )
}

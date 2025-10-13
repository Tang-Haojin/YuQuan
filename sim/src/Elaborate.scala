package sim.top

import chipsalliance.rocketchip.config.Parameters

object Elaborate extends App {
  implicit var p: Parameters = (new sim.SimConfig).alter(cpu.cache.CacheConfig.f).alterPartial({ case cpu.GEN_NAME => if (args.contains("zmb")) "zmb" else "ysyx" })

  if (args.contains("FLASH")) p = p.alterPartial({ case cpu.USEFLASH => true })

  (new circt.stage.ChiselStage).execute(
    Array("--target", "systemverilog", "--split-verilog") ++ args,
    Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new sim.cpu.TestTop)) :+
    circt.stage.FirtoolOption("--default-layer-specialization=enable")
  )
}

object Elaborate extends App {
  implicit var p = (new sim.SimConfig).alter(cpu.cache.CacheConfig.f).alterPartial({ case cpu.GEN_NAME => if (args.contains("zmb")) "zmb" else "ysyx" })

  if (args.contains("FLASH")) p = p.alterPartial({ case cpu.USEFLASH => true })

  (new circt.stage.ChiselStage).execute(
    Array("--target", "verilog") ++ args.take(2),
    Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new sim.cpu.TestTop))
  )
}

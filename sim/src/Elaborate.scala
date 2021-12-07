object Elaborate extends App {
  implicit var p = (new sim.SimConfig).alter(cpu.cache.CacheConfig.f).alterPartial({ case cpu.GEN_NAME => "ysyx" })

  if (args.contains("FLASH"))    p = p.alterPartial({ case cpu.USEFLASH    => true })

  (new chisel3.stage.ChiselStage).execute(args.take(2), Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new sim.cpu.TestTop)))
}

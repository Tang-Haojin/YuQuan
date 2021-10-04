object Elaborate extends App {
  implicit private val p = cpu.YQConfig().alter(cpu.cache.CacheConfig.f).alterPartial({ case cpu.GEN_NAME => if (args.contains("zmb")) "zmb" else "ysyx" })

  (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new cpu.CPU)))
}

object Elaborate extends App {
  implicit private val p = chipsalliance.rocketchip.config.Parameters.empty.alterPartial({ case cpu.GEN_NAME =>
    if (args.contains("zmb")) "zmb"
    else if (args.contains("lxb")) "lxb"
    else "ysyx"
  }).alter(cpu.YQConfig()).alter(cpu.cache.CacheConfig.f)

  (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new cpu.CPU)))
}

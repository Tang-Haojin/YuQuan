object Elaborate extends App {
  implicit var p = (new sim.SimConfig).asInstanceOf[chipsalliance.rocketchip.config.Parameters]

  if (args.contains("CHIPLINK")) p = p.alterPartial({ case sim.USECHIPLINK => true })
  if (args.contains("FLASH"))    p = p.alterPartial({ case cpu.USEFLASH    => true })
  if (args.contains("UART"))     p = p.alterPartial({ case sim.ISREALUART  => true })

  (new chisel3.stage.ChiselStage).execute(args.take(2), Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new sim.cpu.TestTop)))
  // (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new cpu.function.mul.MultiTop)))
}

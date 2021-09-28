import chipsalliance.rocketchip.config._
object Elaborate extends App {
  implicit var p = (new cpu.YQConfig).asInstanceOf[chipsalliance.rocketchip.config.Parameters]

  if (args.contains("zmb")) p = p.alterPartial(cpu.YQConfig.zmb)
  (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new cpu.CPU)))
}

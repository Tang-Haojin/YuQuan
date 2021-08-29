import chipsalliance.rocketchip.config._
object Elaborate extends App {
  implicit val p = (new cpu.YQConfig).toInstance
  (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new cpu.CPU)))
}

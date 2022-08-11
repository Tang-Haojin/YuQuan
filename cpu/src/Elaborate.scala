object Elaborate extends App {
  implicit private val p = chipsalliance.rocketchip.config.Parameters.empty.alterPartial({ case cpu.GEN_NAME =>
    if (args.contains("zmb")) "zmb"
    else if (args.contains("lxb")) "lxb"
    else "ysyx"
  }).alter(cpu.YQConfig()).alter(cpu.cache.CacheConfig.f)

  private var desiredName = ""
  (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => { val CPU = new cpu.CPU; desiredName = CPU.desiredName; CPU })))

  import scala.language.postfixOps
  import sys.process._
  private def parse(args: List[String]): String = args match {
    case "-td" :: ans :: tail => ans
    case "--target-dir" :: ans :: tail => ans
    case Nil => ("pwd" !!).filter(_ != '\n')
    case others => parse(others.drop(1))
  }
  if (p(utils.AXIRENAME)) {
    val finalPath = s"${parse(args.toList)}/${desiredName}.v"
    s"sed -i -e 's/_\\(aw\\|ar\\|w\\|r\\|b\\)_\\(\\|bits_\\)/_\\1/g' ${finalPath}" !!
  }
}

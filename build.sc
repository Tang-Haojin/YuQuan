// import Mill dependency
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.TestModule.Utest
// support BSP
import mill.bsp._

trait BaseScalaModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = "2.13.7"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit"
  )
  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.5.0"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:3.5.0"
  )
}

object sim extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(cpu, peripheral, external)
}

object cpu extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(peripheral, utils, external)
}

object peripheral extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(utils, external)
}

object utils extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(external)
}

object external extends BaseScalaModule

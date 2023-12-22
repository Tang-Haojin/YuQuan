// import Mill dependency
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.TestModule.Utest
// support BSP
import mill.bsp._

trait BaseScalaModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = "2.13.12"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit"
  )
  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.0.0-RC1"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.0.0-RC1"
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

// import Mill dependency
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.TestModule.Utest
// support BSP
import mill.bsp._

trait BaseScalaModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = "2.12.13"
  override def scalacOptions = Seq(
    "-Xsource:2.11",
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    // Enables autoclonetype2 in 3.4.x (on by default in 3.5)
    "-P:chiselplugin:useBundlePlugin"
  )
  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.4.3",
    ivy"edu.berkeley.cs::rocketchip:1.2.6"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:3.4.3",
    ivy"org.scalamacros:::paradise:2.1.1"
  )
}

object sim extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(cpu, peripheral)
}

object cpu extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(peripheral, utils)
}

object peripheral extends BaseScalaModule {
  override def moduleDeps = super.moduleDeps ++ Seq(utils)
}

object utils extends BaseScalaModule

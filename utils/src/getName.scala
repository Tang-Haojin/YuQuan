package utils

trait HasGetName {
  implicit class ImpGetName[T <: chisel3.Data](x: T) {
    def getName: String = chisel3.myHack.GetName(x)
  }
}

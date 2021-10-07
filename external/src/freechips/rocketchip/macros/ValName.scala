// Copyright 2016-2017 SiFive, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package freechips.rocketchip.macros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

case class ValNameImpl(name: String)

object ValNameImpl
{
  implicit def materialize: ValNameImpl = macro detail
  def detail(c: Context): c.Expr[ValNameImpl] = {
    import c.universe._
    def allOwners(s: c.Symbol): Seq[c.Symbol] =
      if (s == `NoSymbol`) Nil else s +: allOwners(s.owner)
    val terms = allOwners(c.internal.enclosingOwner).filter(_.isTerm).map(_.asTerm)
    terms.filter(t => t.isVal || t.isLazy).map(_.name.toString).find(_(0) != '$').map { s =>
      val trim = s.replaceAll("\\s", "")
      c.Expr[ValNameImpl] { q"_root_.freechips.rocketchip.macros.ValNameImpl(${trim})" }
    }.getOrElse(c.abort(c.enclosingPosition, "Not a valid application."))
  }
}

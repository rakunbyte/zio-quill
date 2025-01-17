package io.getquill.context.sql.norm

import io.getquill.ast._

object ExpandMappedInfix {
  def apply(q: Ast): Ast = {
    Transform(q) {
      case Map(Infix("" :: parts, (q: Query) :: params, pure, tr, quat), x, p) =>
        Infix("" :: parts, Map(q, x, p) :: params, pure, tr, quat)
    }
  }
}

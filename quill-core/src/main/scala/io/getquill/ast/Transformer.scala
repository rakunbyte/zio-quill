package io.getquill.ast

trait Transformer[T] {

  val state: T

  def apply(e: Ast): (Ast, Transformer[T]) =
    e match {
      case e: Query     => apply(e)
      case e: Function  => apply(e)
      case e: Operation => apply(e)
      case e: Ref       => apply(e)
      case e: Action    => apply(e)
    }

  def apply(e: Query): (Query, Transformer[T]) =
    e match {
      case e: Table => (e, this)
      case Filter(a, b, c) =>
        val (at, att) = apply(a)
        val (ct, ctt) = att.apply(c)
        (Filter(at, b, ct), ctt)
      case Map(a, b, c) =>
        val (at, att) = apply(a)
        val (ct, ctt) = att.apply(c)
        (Map(at, b, ct), ctt)
      case FlatMap(a, b, c) =>
        val (at, att) = apply(a)
        val (ct, ctt) = att.apply(c)
        (FlatMap(at, b, ct), ctt)
    }

  def apply(e: Function): (Function, Transformer[T]) =
    e match {
      case Function(params, body) =>
        val (paramst, t) =
          params.foldLeft((List[Ident](), this)) {
            case ((values, t), v) =>
              val (vt, vtt) = apply(v)
              (values :+ vt, vtt)
          }
        val (bodyt, bt) = t.apply(body)
        (Function(paramst, bodyt), bt)
    }

  def apply(e: Operation): (Operation, Transformer[T]) =
    e match {
      case UnaryOperation(o, a) =>
        val (at, att) = apply(a)
        (UnaryOperation(o, at), att)
      case BinaryOperation(a, b, c) =>
        val (at, att) = apply(a)
        val (ct, ctt) = att.apply(c)
        (BinaryOperation(at, b, ct), ctt)
      case FunctionApply(function, values) =>
        val (functiont, functiontt) = apply(function)
        val (valuest, valuestt) =
          values.foldLeft((List[Ast](), functiontt)) {
            case ((values, t), v) =>
              val (vt, vtt) = apply(v)
              (values :+ vt, vtt)
          }
        (FunctionApply(functiont, valuest), valuestt)
    }

  def apply(e: Ref): (Ref, Transformer[T]) =
    e match {
      case e: Property => apply(e)
      case e: Ident    => apply(e)
      case e: Value    => apply(e)
    }

  def apply(e: Property): (Property, Transformer[T]) =
    e match {
      case Property(a, name) =>
        val (at, att) = apply(a)
        (Property(at, name), att)
    }

  def apply(e: Ident): (Ident, Transformer[T]) =
    (e, this)

  def apply(e: Value): (Value, Transformer[T]) =
    e match {
      case e: Constant => (e, this)
      case NullValue   => (e, this)
      case Tuple(values) =>
        val (valuest, valuestt) = apply(values)(apply)
        (Tuple(valuest), valuestt)
    }

  def apply(e: Action): (Action, Transformer[T]) =
    e match {
      case Update(query, assignments) =>
        val (queryt, querytt) = apply(query)
        val (at, att) = apply(assignments)(apply)
        (Update(queryt, at), att)
      case Insert(query, assignments) =>
        val (queryt, querytt) = apply(query)
        val (at, att) = apply(assignments)(apply)
        (Insert(queryt, at), att)
      case Delete(query) =>
        val (qt, qtt) = apply(query)
        (Delete(query), qtt)
    }

  def apply(e: Assignment): (Assignment, Transformer[T]) =
    e match {
      case Assignment(a, b) =>
        val (at, att) = apply(a)
        val (bt, btt) = att.apply(b)
        (Assignment(at, bt), btt)
    }

  private def apply[U](list: List[U])(f: U => (U, Transformer[T])) =
    list.foldLeft((List[U](), this)) {
      case ((values, t), v) =>
        val (vt, vtt) = f(v)
        (values :+ vt, vtt)
    }
}
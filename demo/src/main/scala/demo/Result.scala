package demo

import dvvset.{Clock, VersionVector}

sealed trait Result[+V] {
  def version: VersionVector[Node]

  def hasValue: Boolean
}

object Result {
  implicit class ClockOps[V](val clock: Clock[Node, Option[V]]) {
    def toResult: Result[V] =
      clock.values match {
        case head :: Nil if head.isEmpty => Deleted(clock.version)
        case Some(head) :: Nil => Just(head, clock.version)
        case values =>
          Conflict(values.filter(_.isDefined).map(_.get).toSet, clock.version, withDeleted = values.exists(_.isEmpty))
      }
  }
}

case class Just[V](value: V, version: VersionVector[Node]) extends Result[V] {
  override def hasValue: Boolean = true
}

case class Deleted[V](version: VersionVector[Node]) extends Result[V] {
  override def hasValue: Boolean = false
}

case class Conflict[V](values: Set[V], version: VersionVector[Node], withDeleted: Boolean) extends Result[V] {
  override def hasValue: Boolean = true
}

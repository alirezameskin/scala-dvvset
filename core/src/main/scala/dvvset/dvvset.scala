package dvvset

import scala.annotation.tailrec

case class VersionVector[R](versions: List[(R, Long)])

object VersionVector {
  def empty[R]: VersionVector[R] = VersionVector(List.empty)
}

case class Entry[R, V](id: R, counter: Long, values: List[V])

case class Clock[R: Ordering, V](entries: List[Entry[R, V]], anonymousValues: List[V]) {

  /**
   * Advances the causal history with the given replica id. The new value is the *anonymous dot* of the clock.
   */
  def update(replica: R): Clock[R, V] =
    Clock.update(this, replica)

  /**
   * Advances the causal history of the this clock with the given replica, while synchronizing with the provided clock,
   * thus the new clock is causally newer than both clocks in the argument. The new value is the *anonymous dot* of the
   * clock.
   */
  def update(other: Clock[R, V], replica: R): Clock[R, V] =
    Clock.update(this, other, replica)

  /**
   * Returns the total number of values in this clock set.
   *
   * @return
   */
  def size: Int =
    entries.map(e => e.values.length).sum + anonymousValues.length

  /**
   * Returns all the ids used in this clock set.
   */
  def ids: List[R] =
    entries.map(e => e.id)

  /**
   * Returns all the values used in this clock set, including the anonymous values.
   */
  def values: List[V] =
    this.anonymousValues ++ this.entries.flatMap(e => e.values)

  /**
   * Return a version vector that represents the causal history.
   */
  def version: VersionVector[R] =
    VersionVector(this.entries.map(e => (e.id, e.counter)))

  /**
   * Returns True if the first clock is causally older than the second clock, thus values on the first clock are
   * outdated. Returns False otherwise.
   */
  def less(other: Clock[R, V]): Boolean =
    Clock.greater(other.entries, this.entries, strict = false)

}

object Clock {

  /**
   * Constructs a new clock set with the given value.
   */
  def from[R: Ordering, V](value: V): Clock[R, V] =
    Clock(List.empty, List(value))

  /**
   * Constructs a new clock set with the given values.
   */
  def from[R: Ordering, V](values: List[V]): Clock[R, V] =
    Clock(List.empty, values)

  /**
   * Constructs a new clock set with the causal history.
   */
  def from[R: Ordering, V](vector: VersionVector[R], value: V): Clock[R, V] =
    from(vector, List(value))

  /**
   * Constructs a new clock set with the causal history.
   */
  def from[R: Ordering, V](vector: VersionVector[R], values: List[V]): Clock[R, V] = {
    val replicaOrdering = implicitly[Ordering[R]]
    val ordering: Ordering[(R, Long)] = Ordering.by[(R, Long), R](_._1)(replicaOrdering)

    val versions: List[(R, Long)] = vector.versions.sorted(ordering)
    val entries: List[Entry[R, V]] = versions.map(v => Entry(v._1, v._2, List.empty))
    Clock(entries, values)
  }

  /**
   * Synchronizes a list of clocks,It discards (causally) outdated values, while merging all causal histories.
   */
  def sync[R: Ordering, V](clocks: List[Clock[R, V]]): Clock[R, V] =
    clocks.foldLeft(Clock[R, V](Nil, Nil))((acc, clock) => sync(acc, clock))

  private def update[R: Ordering, V](clock: Clock[R, V], replica: R): Clock[R, V] =
    clock match {
      case Clock(entries, head :: _) =>
        Clock[R, V](event(entries, replica, head), List.empty)
      case _ => clock
    }

  private def event[R, V](entries: List[Entry[R, V]], replica: R, value: V)(implicit
    ord: Ordering[R]
  ): List[Entry[R, V]] =
    entries match {
      case Nil =>
        List(Entry(replica, 1, List(value)))

      case head :: next if ord.equiv(head.id, replica) =>
        head.copy(counter = head.counter + 1, values = List(value) ++ head.values) :: next

      case head :: next if ord.gt(head.id, replica) =>
        Entry(replica, 1, List(value)) :: head :: next

      case head :: next =>
        head :: event(next, replica, value)
    }

  private def update[R: Ordering, V](clock1: Clock[R, V], clock2: Clock[R, V], replica: R): Clock[R, V] =
    clock1.anonymousValues match {
      case Nil =>
        clock2
      case head :: _ =>
        val c1 = sync(Clock(clock1.entries, List.empty), clock2)
        Clock(event(c1.entries, replica, head), c1.anonymousValues)
    }

  private def sync[R: Ordering, V](clock1: Clock[R, V], clock2: Clock[R, V]): Clock[R, V] =
    (clock1, clock2) match {
      case (Clock(Nil, _), _) => clock2
      case (_, Clock(Nil, _)) => clock1
      case (Clock(entries1, values1), Clock(entries2, values2)) =>
        if (clock1.less(clock2)) {
          Clock(syncEntries(entries1, entries2), values2)
        } else {

          if (clock2.less(clock1)) {
            Clock(syncEntries(entries2, entries1), values1)
          } else {
            Clock(syncEntries(entries1, entries2), (values1 ++ values2).distinct)
          }
        }
    }

  private def syncEntries[R, V](entries1: List[Entry[R, V]], entries2: List[Entry[R, V]])(implicit
    ordering: Ordering[R]
  ): List[Entry[R, V]] = {
    (entries1, entries2) match {
      case (Nil, _) => entries2
      case (_, Nil) => entries1
      case (head1 :: next1, head2 :: next2) =>
        if (ordering.lt(head1.id, head2.id)) {
          head1 :: syncEntries(next1, entries2)
        } else if (ordering.gt(head1.id, head2.id)) {
          head2 :: syncEntries(next2, entries1)
        } else {
          val result = merge(head1.id, head1.counter, head1.values, head2.counter, head2.values)
          List(Entry(result._1, result._2, result._3)) ++ syncEntries(next1, next2)
        }
    }
  }

  private def merge[R: Ordering, V](replica: R, c1: Long, values1: List[V], c2: Long, values2: List[V]) = {
    val len1 = values1.length
    val len2 = values2.length

    if (c1 >= c2) {
      if (c1 - len1 >= c2 - len2)
        (replica, c1, values1)
      else
        (replica, c1, values1.slice(0, (c1 - c2 + len2).toInt))
    } else {

      if (c2 - len2 >= c1 - len1)
        (replica, c2, values2)
      else
        (replica, c2, values2.slice(0, (c2 - c1 + len1).toInt))
    }
  }

  @tailrec
  private def greater[R, V](clock1: List[Entry[R, V]], clock2: List[Entry[R, V]], strict: Boolean)(implicit
    ordering: Ordering[R]
  ): Boolean =
    (clock1, clock2) match {
      case (Nil, Nil) => strict
      case (_, Nil)   => true
      case (Nil, _)   => false
      case (head1 :: next1, head2 :: _) if ordering.lt(head1.id, head2.id) =>
        greater(next1, clock2, strict = true)
      case (head1 :: next1, head2 :: next2) if ordering.equiv(head1.id, head2.id) =>
        if (head1.counter == head2.counter) greater(next1, next2, strict)
        else if (head1.counter > head2.counter) greater(next1, next2, strict = true)
        else false
      case _ => false
    }
}

package demo

import demo.Result._
import dvvset.{Clock, VersionVector}

import java.util.concurrent.ConcurrentHashMap

case class Node(address: String)

class KeyValueStore[V](val replica: Node) {
  implicit val ordering: Ordering[Node] = (x: Node, y: Node) => x.address.compareTo(y.address)

  private val map = new ConcurrentHashMap[String, Clock[Node, Option[V]]]

  def put(key: String, value: V): Result[V] = {
    map
      .compute(
        key,
        (_, v) => {
          v match {
            case null => Clock.from[Node, Option[V]](Some(value)).update(replica)
            case localClock => Clock.from[Node, Option[V]](Some(value)).update(localClock, replica)
          }
        }
      )
      .toResult
  }

  def put(key: String, value: V, version: VersionVector[Node]): Result[V] =
    map
      .compute(
        key,
        (_, v) => {
          v match {
            case null => Clock.from[Node, Option[V]](version, Some(value))
            case localClock => Clock.from[Node, Option[V]](version, Some(value)).update(localClock, replica)
          }
        }
      )
      .toResult

  def get(key: String): Option[Result[V]] =
    Option(map.get(key)).map(_.toResult).filter(_.hasValue)

  def delete(key: String): Result[V] =
    map
      .compute(
        key,
        (_, v) => {
          v match {
            case null => Clock.from[Node, Option[V]](Option.empty).update(replica)
            case localClock => Clock.from[Node, Option[V]](None).update(localClock, replica)
          }
        }
      )
      .toResult

  def delete(key: String, version: VersionVector[Node]): Result[V] =
    map
      .compute(key,
        (_, v) => {
          v match {
            case null => Clock.from(version, Option.empty)
            case localClock => Clock.from(version, Option.empty[V]).update(localClock, replica)
          }
        }
      )
      .toResult
}

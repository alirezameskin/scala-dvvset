package dvvset

import org.scalatest.funsuite.AnyFunSuite

class dvvsetSpec extends AnyFunSuite {

  case class Replica(id: String) {
    override def toString: String = id.toUpperCase
  }

  implicit val ordering: Ordering[Replica] = (x: Replica, y: Replica) => x.id.compareTo(y.id)

  test("version spec") {
    val nodeA = Replica("a")
    val nodeB = Replica("b")

    val a0 = Clock.from("v1")
    val a = a0.update(nodeA)
    val b = Clock
      .from(a.version, "v2")
      .update(a, nodeB)

    assert(a0.version == VersionVector.empty)
    assert(a.version == VersionVector[Replica](List(nodeA -> 1)))
    assert(b.version == VersionVector[Replica](List(nodeA -> 1, nodeB -> 1)))
  }

  test("update spec") {
    val nodeA = Replica("a")
    val nodeB = Replica("b")

    val a0 = Clock.from("v1").update(nodeA)
    val a1 = Clock.from(a0.version, "v2").update(a0, nodeA)
    val a2 = Clock.from(a1.version, "v3").update(a1, nodeB)
    val a3 = Clock.from(a0.version, "v4").update(a1, nodeB)
    val a4 = Clock.from(a0.version, "v5").update(a1, nodeA)

    assert(a0 == Clock[Replica, String](List(Entry(nodeA, 1, List("v1"))), List.empty))
    assert(a1 == Clock[Replica, String](List(Entry(nodeA, 2, List("v2"))), List.empty))
    assert(a2 == Clock[Replica, String](List(Entry(nodeA, 2, List.empty), Entry(nodeB, 1, List("v3"))), List.empty))
    assert(a3 == Clock[Replica, String](List(Entry(nodeA, 2, List("v2")), Entry(nodeB, 1, List("v4"))), List.empty))
    assert(a4 == Clock[Replica, String](List(Entry(nodeA, 3, List("v5", "v2"))), List.empty))
  }

  test("less spec") {
    val replicaA = Replica("a")
    val replicaB = Replica("b")
    val replicaC = Replica("c")
    val replicaZ = Replica("z")

    val A = Clock.from("v1").update(replicaA)
    val B = Clock.from(A.version, "v2").update(replicaA)
    val B2 = Clock.from(A.version, "v2").update(replicaB)
    val B3 = Clock.from(A.version, "v2").update(replicaZ)
    val C = Clock.from(B.version, "v3").update(A, replicaC)
    val D = Clock.from(C.version, "v4").update(B2, replicaA)

    assert(A.less(C))
    assert(A.less(B))
    assert(B.less(C))
    assert(B.less(D))
    assert(B2.less(D))
    assert(A.less(D))

    assert(!B2.less(C))
    assert(!B.less(B2))
    assert(!B2.less(B))
    assert(!A.less(A))
    assert(!C.less(C))
    assert(!D.less(B2))
    assert(!B3.less(D))
  }

  test("size spec") {
    assert(1 == Clock.from("v1").size)

    val replicaA = Replica("a")
    val replicaB = Replica("b")
    val replicaC = Replica("c")

    val clock = Clock[Replica, String](
      List(
        Entry(replicaA, 4, List("v5", "v0")),
        Entry(replicaB, 0, List()),
        Entry(replicaC, 1, List("v3"))
      ),
      List("v4", "v1")
    )
    assert(5 == clock.size)
  }

  test("test ids") {
    val replicaA = Replica("a")
    val replicaB = Replica("b")
    val replicaC = Replica("c")

    val clock = Clock[Replica, String](
      List(
        Entry(replicaA, 4, List("v5", "v0")),
        Entry(replicaB, 0, List()),
        Entry(replicaC, 1, List("v3"))
      ),
      List("v4", "v1")
    )
    assert(List(replicaA, replicaB, replicaC) == clock.ids)
  }

  test("test update") {
    val replicaA = Replica("a")
    val replicaB = Replica("b")

    val a0 = Clock.from("v1").update(replicaA)
    val a1 = Clock
      .from(a0.version, "v2")
      .update(a0, replicaA)

    val a2 = Clock
      .from(a1.version, "v3")
      .update(a1, replicaB)

    val a3 = Clock
      .from(a0.version, "v4")
      .update(a1, replicaB)

    val a4 = Clock
      .from(a0.version, "v5")
      .update(a1, replicaA)

    assert(List("v5", "v2") == a4.values)
    assert(Clock(List(Entry(replicaA, 1, List("v1"))), List.empty) == a0)
    assert(Clock(List(Entry(replicaA, 2, List("v2"))), List.empty) == a1)
    assert(Clock(List(Entry[Replica, String](replicaA, 2, List()), Entry(replicaB, 1, List("v3"))), List()) == a2)
    assert(Clock(List(Entry(replicaA, 2, List("v2")), Entry(replicaB, 1, List("v4"))), List()) == a3)
    assert(Clock(List(Entry(replicaA, 3, List("v5", "v2"))), List()) == a4)
  }
}

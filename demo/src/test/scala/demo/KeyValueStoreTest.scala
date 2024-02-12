package demo

import dvvset.VersionVector
import org.scalatest.funsuite.AnyFunSuite

class KeyValueStoreTest extends AnyFunSuite {

  test("dddd") {
    val replica = Node("a")
    val store = new KeyValueStore[String](replica)
    val u1 = store.put("key", "v1")

    // There is only one value for that Key
    assert(store.get("key").contains(Just("v1", VersionVector(List(replica -> 1)))))

    // Update the value with a new version
    val u2 = store.put("key", "v2", u1.version)

    // The value is updated with latest version
    assert(store.get("key").contains(Just("v2", VersionVector(List(replica -> 2)))))

    // Updating the key with old version
    val u3 = store.put("key", "v3", u1.version)

    // Now there is a conflict with the old version
    assert(store.get("key").contains(Conflict(Set("v3", "v2"), VersionVector(List(replica -> 3)), withDeleted = false)))

    // Update the value one more time with the last version (Resolving the conflict)
    val u4 = store.put("key", "v4", u3.version)

    // The value is updated with latest version without conflict
    assert(store.get("key").contains(Just("v4", VersionVector(List(replica -> 4)))))

    // Delete the key
    val d1 = store.delete("key", u4.version)

    // The key is deleted
    assert(store.get("key").isEmpty)
  }

  test("put") {
    val replica = Node("a")
    val store = new KeyValueStore[String](replica)
    val r1 = store.put("key", "v1")

    assert(store.get("key").contains(Just("v1", VersionVector(List(replica -> 1)))))

    val r2 = store.put("key", "v2", r1.version)

    assert(store.get("key").contains(Just("v2", VersionVector(List(replica -> 2)))))

    assert(store.get("key").contains(Just("v2", VersionVector(List(replica -> 2)))))
  }

  test("delete existing key without conflict") {
    val replica = Node("a")
    val store = new KeyValueStore[String](replica)
    val r1 = store.put("key", "v1")
    val r2 = store.put("key", "v2", r1.version)
    store.delete("key", r2.version)

    assert(store.get("key").isEmpty)
  }

  test("delete existing key") {
    val replica = Node("a")
    val store = new KeyValueStore[String](replica)
    val r1 = store.put("key", "v1")
    val r2 = store.put("key", "v2", r1.version)
    val d1 = store.delete("key")

    val result = store.get("key")

    assert(result.isDefined)
    assert(result.get.isInstanceOf[Conflict[String]])
    assert(result.get.asInstanceOf[Conflict[String]].withDeleted)
    assert(result.get.asInstanceOf[Conflict[String]].values == Set("v2"))
  }

  test("delete non existing key") {
    val replica = Node("a")
    val store = new KeyValueStore[String](replica)
    val r1 = store.put("key", "v1")
    val r2 = store.put("key", "v2", r1.version)

    store.delete("InvalidKey", r2.version)

    assert(store.get("key").isDefined)
    assert(store.get("key").contains(Just("v2", VersionVector(List((replica, 2))))))
  }

  test("concurrent deletes") {
    val replicaA = Node("A")

    val store = new KeyValueStore[String](replicaA)
    val r1 = store.put("key", "v1")
    val r2 = store.put("key", "v2", r1.version)

    val d1 = store.delete("key", r1.version);
    val d2 = store.delete("key", r1.version)
    val u3 = store.put("key", "v3", r2.version)

    assert(d1.isInstanceOf[Conflict[String]])
    assert(d2.isInstanceOf[Conflict[String]])
    assert(u3.isInstanceOf[Conflict[String]])
  }
}

# scala-dvvset

A Dotted Version Vector Sets implementation in Scala

Scala version of the [Dotted Version Vector Sets](https://github.com/ricardobcl/Dotted-Version-Vectors) Erlang reference implementation. This data structure
used in distributed systems to track the causality relationships between events.

## Usage

### Creating a new version without history

```scala
import dvvset._

val replicaA = Replica("a")
val dvv = Clock.from("v1")
  .update(replicaA)

println(dvv) // Clock([(replicaA, 1, [v1])], [])
```

### Create a new version with history

```scala
import dvvset._

val replicaA = Replica("a")
val dvv0 = Clock.from("v1").update(replica)

// Create a new version with history
val dvv1 = Clock.from(dvv0.version, "v2")
  .update(dvv0, replica)

println(dvv1) // Clock([(replicaA, 2, [v2])], [])
```

### Conflicting versions

```scala
import dvvset._
val replica = Replica("a")

val dvv0 = Clock.from("v1").update(replica)

val dvv1 = Clock.from(dvv0.version, "v2")
    .update(dvv0, replica)

// Create a new version with old history and updated with a new version
val dvv2 = Clock.from(dvv0.version, "v3")
    .update(dvv1, replica)

println(dvv1) // Clock([(replicaA, 2, [v2])], [])
println(dvv2) // Clock([(replicaA, 3, [v3, v2])], [])
```

## Real world usage

There is also a simple KeyValue store implementation using the DVVSet in `demo/src/main/scala/demo/KeyValueStore.scala`.

```scala

import dvvset._
import demo.KeyValueStore
import demo.Node

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

```




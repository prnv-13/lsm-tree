# LSM Tree

A small generic key-value store built on a **Log-Structured Merge tree**, with a
memtable, immutable SSTables and tombstones for deletes.

## Problem

```java
LSMTree<K extends Comparable<K>, V>

void insert(K key, V value)  // add or overwrite
V    select(K key)           // latest value, or null if missing/deleted
void delete(K key)           // remove
```

| Component | Role |
|---|---|
| **Memtable** | In-memory `HashMap<K, Value<V>>` that receives every write. Holds at most `N` entries. |
| **SSTable** | Immutable, sorted run of entries, created when a full memtable is flushed. |
| **Value** | Wraps a stored value. `Value.tombstone()` marks a deleted key. |

### Rules

- Every write (insert and delete) goes to the memtable only.
- When the memtable reaches `N` entries, it's flushed to a new SSTable and replaced with an empty one.
- SSTables are never modified after they're created.
- Tombstones count toward `N`. Overwriting a key already in the memtable does not.
- `select` always returns the most recent state of a key.
- `null` values are rejected.

### Example (N = 2)

```
insert(5, "a")   → memtable {5:a}
insert(3, "b")   → memtable {3:b, 5:a} → FULL → flush → SST1 [3:b, 5:a]
delete(5)        → memtable {5:☠}
select(5)        → null      (SST1 still has 5:a, but the tombstone is newer)
insert(7, "c")   → memtable {5:☠, 7:c} → flush → SST2 [5:☠, 7:c]
select(3)        → "b"
select(5)        → null
insert(5, "d")
select(5)        → "d"
select(42)       → null
```

## Layout

```
src/main/java/lsm/
  Value.java       value or tombstone (done)
  Memtable.java    steps 1–3
  SSTable.java     step 4
  LSMTree.java     step 5
  Main.java        runs the example above
src/test/java/lsm/
  Step1MemtableTest … Step5LSMTreeTest
```

## Steps

Each unfinished method throws `UnsupportedOperationException("TODO: Step N")`.
Make each step's tests pass before moving on.

| Step | Implement | Tests |
|---|---|---|
| 1 | `Memtable.put`, `get`, `size` | `Step1MemtableTest` |
| 2 | `Memtable.delete` | `Step2TombstoneTest` |
| 3 | `Memtable.isFull`, `toSSTable` | `Step3FlushTest` |
| 4 | `SSTable.get` | `Step4SSTableTest` |
| 5 | `LSMTree.insert`, `delete`, `select`, `flushIfFull` | `Step5LSMTreeTest` |

```bash
mvn test
```

```bash
mvn test -Dtest=Step1MemtableTest
```

```bash
mvn compile exec:java -Dexec.mainClass=lsm.Main
```

## Hints

1. Why can't `delete` just call `map.remove`? Trace the example and see where `select(5)` finds a value.
2. A `HashMap` has no order, but an SSTable must be sorted.
3. Don't drop tombstones on flush. An older SSTable might still have the key.
4. The SSTable keys are sorted. Read the docs for `Collections.binarySearch`, especially what it returns when the key is missing.
5. `select` searches newest to oldest. Choose which end of the SSTable list is newest and stay consistent.
6. At each layer there are three outcomes: a value, a tombstone, or nothing. Decide what each one means before writing the loop.

## Stretch goals

- [ ] **Compaction**: merge SSTables into one (k-way merge, newest wins). When is it safe to drop a tombstone?
- [ ] **Range scan**: `scan(lo, hi)` across all layers, respecting tombstones.
- [ ] **Bloom filter** per SSTable to skip tables that can't contain the key.
- [ ] **Write-ahead log** so the memtable survives a crash.
- [ ] **Persist SSTables to disk.**

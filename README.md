# LSM Tree

A small generic key-value store built on a **Log-Structured Merge tree**, with a
memtable, immutable SSTables and tombstones for deletes.

## Problem

```java
LSMTree<K extends Comparable<K>, V>

void insert(K key, V value)  // add or overwrite
V    select(K key)           // latest value, or null if missing/deleted
void delete(K key)           // remove
void compact()               // merge all SSTables into one
List<Map.Entry<K, V>> scan(K lo, K hi)  // live keys in [lo, hi), sorted
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
  Memtable.java    steps 1–3, 7
  SSTable.java     steps 4, 7
  LSMTree.java     steps 5–7
  Compactor.java   step 6
  Main.java        runs the example above
src/test/java/lsm/
  Step1MemtableTest … Step7RangeScanTest
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
| 6 | `Compactor.merge`, `LSMTree.compact` | `Step6CompactionTest` |
| 7 | `SSTable.range`, `Memtable.range`, `LSMTree.scan` | `Step7RangeScanTest` |

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

## Compaction (Step 6)

Every flush adds an SSTable, so reads get slower and deleted or overwritten data is never reclaimed.
Compaction merges SSTables into one.

```
SST1 [1:a, 2:b]      SST2 [1:☠, 3:c]      SST3 [2:z]
                         │
               compact() │  newest wins per key, tombstones dropped
                         ▼
                  SST  [2:z, 3:c]
```

- `Compactor.merge(tables, dropTombstones)` takes tables ordered **oldest to newest** and returns one sorted SSTable.
- For each key, only the entry from the newest table survives.
- `dropTombstones` decides whether tombstones (and the values they hide) are removed or kept.
- `LSMTree.compact()` merges **all** SSTables into one. The memtable is not touched.
  If the merged table is empty, the tree ends up with no SSTables.

### Hints

7. Start simple: walk the tables oldest to newest and put every entry into a sorted map, so newer entries overwrite older ones. Then build the SSTable from the map.
8. The real version is a **k-way merge**: every table is already sorted, so keep one cursor per table and a `PriorityQueue` of the smallest current keys. When two tables have the same key, which one wins?
9. When is it safe to drop a tombstone? Ask what could still be *older* than the merged result. Why would dropping one during a partial merge (only the newest few tables) bring deleted data back?
10. Why doesn't compaction need to look at the memtable?

## Range scan (Step 7)

`scan(lo, hi)` returns every live key with `lo <= key < hi`, sorted, with its newest value.
Deleted keys are left out. It reads the memtable and every SSTable.

```
insert(1,"a") insert(3,"c")   → SST1 [1:a, 3:c]
insert(2,"b") insert(3,"C")   → SST2 [2:b, 3:C]
delete(2)     insert(5,"e")   → SST3 [2:☠, 5:e]
insert(4,"d")                 → memtable {4:d}

scan(1, 5)  → [1:a, 3:C, 4:d]
scan(0, 99) → [1:a, 3:C, 4:d, 5:e]
scan(3, 3)  → []
```

- `SSTable.range(lo, hi)` returns the entries in `[lo, hi)` as a new SSTable, tombstones included.
- `Memtable.range(lo, hi)` does the same for the memtable, sorted.
- `lo >= hi` always gives an empty result.

### Hints

11. This is compaction again: each layer gives its keys in the range, then you merge, the newest wins, and tombstones are dropped. Can you reuse `Compactor.merge`?
12. Don't read whole SSTables. The keys in `[lo, hi)` form one block. When `binarySearch` misses, `-(i + 1)` is where the key would go. Use it for both ends, then look at `List.subList`.
13. The memtable is a `HashMap`, so it has no order. Filter and sort, or switch to a `TreeMap` and use `subMap`? Compare the cost of `put`, `get` and `scan` for each. If you use `subMap`, read what it does when `lo > hi`.
14. Where does the memtable go in the oldest-to-newest list you pass to `merge`?
15. Is it safe to drop tombstones here? Compaction could only drop them because nothing older existed.

## Stretch goals

- [ ] **Automatic compaction**: compact when the number of SSTables passes a threshold.
- [ ] **Bloom filter** per SSTable to skip tables that can't contain the key.
- [ ] **Write-ahead log** so the memtable survives a crash.
- [ ] **Persist SSTables to disk.**

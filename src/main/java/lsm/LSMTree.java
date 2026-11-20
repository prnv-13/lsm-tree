package lsm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class LSMTree<K extends Comparable<K>, V> {

    private final int memtableCapacity;
    private Memtable<K, V> memtable;
    private final List<SSTable<K, V>> sstables = new ArrayList<>();

    public LSMTree(int memtableCapacity) {
        this.memtableCapacity = memtableCapacity;
        this.memtable = new Memtable<>(memtableCapacity);
    }

    public void insert(K key, V value) {
        memtable.put(key, value);
        flushIfFull();
    }

    public void delete(K key) {
        memtable.delete(key);
        flushIfFull();
    }

    // null if missing or deleted
    public V select(K key) {
        var value = memtable.get(key);
        if (value != null) {
            return getV(value);
        }

        for (int i = sstableCount(); i > 0; i--) {
            var sstTable = sstables.get(i-1);
            value = sstTable.get(key);
            if (value != null) {
                return getV(value);
            }
        }

        return null;
    }

    private static <V> V getV(Value<V> value) {
        if (value.isTombstone()) return null;
        return value.data();
    }

    private void flushIfFull() {
        if (memtable.isFull()) {
            sstables.add(memtable.toSSTable());
            memtable = new Memtable<>(memtableCapacity);
        }
    }

    public void compact() {
        final var merged = Compactor.merge(sstables, true);
        sstables.clear();
        if (merged.size() != 0) sstables.add(merged);
    }

    // live keys with lo <= key < hi, sorted, newest value wins
    public List<Map.Entry<K, V>> scan(K lo, K hi) {

        final var sstTables = new ArrayList<SSTable<K, V>>();
        for (var table : this.sstables) {
            sstTables.add(table.range(lo, hi));
        }
        sstTables.add(memtable.range(lo, hi));
        final var compacted = Compactor.merge(sstTables, true);

        if (compacted.size() == 0) return List.of();

        List<Map.Entry<K, V>> answer = new ArrayList<>();
        for (int i = 0; i < compacted.size(); i++) {
            answer.add(Map.entry(compacted.keyAt(i), compacted.valueAt(i).data()));
        }

        return answer;
    }

    int sstableCount() {
        return sstables.size();
    }

    SSTable<K, V> sstableAt(int index) {
        return sstables.get(index);
    }
}

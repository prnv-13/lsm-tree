package lsm;

import java.util.ArrayList;
import java.util.List;

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

    int sstableCount() {
        return sstables.size();
    }
}

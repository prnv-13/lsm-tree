package lsm;

import java.util.*;

public class Memtable<K extends Comparable<K>, V> {

    private final int capacity;
    private final TreeMap<K, Value<V>> entries = new TreeMap<>();

    public Memtable(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
    }

    public void put(K key, V value) {
        entries.put(key, Value.of(value));
    }

    // null if the key was never seen
    public Value<V> get(K key) {
        return entries.get(key);
    }

    public int size() {
        return entries.size();
    }

    public void delete(K key) {
        entries.put(key, Value.tombstone());
    }

    public boolean isFull() {
        return size() >= capacity;
    }

    public SSTable<K, V> toSSTable() {

        final var keys = new ArrayList<>(entries.keySet());
        final var valueList = new ArrayList<>(entries.values());

        return new SSTable<>(keys, valueList);
    }

    // sorted entries with lo <= key < hi, tombstones included
    public SSTable<K, V> range(K lo, K hi) {

        if (lo.compareTo(hi) >= 0) return new SSTable<K, V>(List.<K>of(), List.<Value<V>>of());
        final var subMap = entries.subMap(lo, hi);

        final var keys = new ArrayList<>(subMap.keySet());
        final var valueList = new ArrayList<>(subMap.values());

        return new SSTable<>(keys, valueList);
    }
}

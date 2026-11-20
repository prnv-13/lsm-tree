package lsm;

import java.util.*;

public class Memtable<K extends Comparable<K>, V> {

    private final int capacity;
    private final Map<K, Value<V>> entries = new HashMap<>();

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
        Collections.sort(keys);

        int size = keys.size();
        List<Value<V>> valueList = new ArrayList<>(size);

        for (K key : keys) valueList.add(get(key));
        return new SSTable<>(keys, valueList);
    }
}

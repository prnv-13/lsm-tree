package lsm;

import java.util.Collections;
import java.util.List;

public final class SSTable<K extends Comparable<K>, V> {

    private final List<K> keys;
    private final List<Value<V>> values;

    public SSTable(List<K> keys, List<Value<V>> values) {
        if (keys.size() != values.size()) {
            throw new IllegalArgumentException("keys and values must have the same size");
        }
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i - 1).compareTo(keys.get(i)) >= 0) {
                throw new IllegalArgumentException("keys must be strictly ascending at index " + i);
            }
        }
        this.keys = List.copyOf(keys);
        this.values = List.copyOf(values);
    }

    // null if the key is absent
    public Value<V> get(K key) {
        int i = Collections.binarySearch(keys, key);
        if (i < 0) return null;
        return valueAt(i);
    }

    // entries with lo <= key < hi, tombstones included
    public SSTable<K, V> range(K lo, K hi) {

        if (lo.compareTo(hi) >= 0) return new SSTable<K, V>(List.<K>of(), List.<Value<V>>of());

        int loIdx = Collections.binarySearch(keys, lo);
        if (loIdx < 0) loIdx = -(loIdx + 1);

        int hiIdx = Collections.binarySearch(keys, hi);
        if (hiIdx < 0) hiIdx = -(hiIdx + 1);

        final var subKeys = keys.subList(loIdx, hiIdx);
        final var subValues = values.subList(loIdx, hiIdx);

        return new SSTable<>(subKeys, subValues);
    }

    public int size() {
        return keys.size();
    }

    K keyAt(int index) {
        return keys.get(index);
    }

    Value<V> valueAt(int index) {
        return values.get(index);
    }
}

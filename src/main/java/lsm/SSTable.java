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

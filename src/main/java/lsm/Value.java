package lsm;

import java.util.Objects;

public record Value<V>(V data, boolean isTombstone) {

    public static <V> Value<V> of(V data) {
        return new Value<>(Objects.requireNonNull(data, "value must not be null"), false);
    }

    public static <V> Value<V> tombstone() {
        return new Value<>(null, true);
    }
}

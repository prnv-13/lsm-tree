package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 8: Bloom filter")
class Step8BloomFilterTest {

    private static double falsePositiveRate(BloomFilter<Integer> f, int from, int to) {
        int hits = 0;
        for (int k = from; k < to; k++) {
            if (f.mightContain(k)) {
                hits++;
            }
        }
        return hits / (double) (to - from);
    }

    @Test
    void noFalseNegatives() {
        BloomFilter<Integer> f = new BloomFilter<>(10_000, 0.01);
        for (int i = 0; i < 10_000; i++) {
            f.add(i);
        }
        for (int i = 0; i < 10_000; i++) {
            assertTrue(f.mightContain(i), "added key " + i);
        }
    }

    @Test
    void falsePositiveRateIsNearTarget() {
        BloomFilter<Integer> f = new BloomFilter<>(10_000, 0.01);
        for (int i = 0; i < 10_000; i++) {
            f.add(i);
        }
        double rate = falsePositiveRate(f, 10_000, 110_000);
        assertTrue(rate < 0.03, "false positive rate was " + rate);
    }

    @Test
    void stridedKeysStillSpreadAcrossBits() {
        BloomFilter<Integer> f = new BloomFilter<>(5_000, 0.01);
        for (int i = 0; i < 5_000; i++) {
            f.add(i * 1024);
        }
        int hits = 0;
        for (int i = 0; i < 50_000; i++) {
            if (f.mightContain(i * 1024 + 512)) {
                hits++;
            }
        }
        assertTrue(hits / 50_000.0 < 0.03, "false positive rate was " + hits / 50_000.0);
    }

    @Test
    void verySmallTargetRate() {
        BloomFilter<Integer> f = new BloomFilter<>(10_000, 0.0001);
        for (int i = 0; i < 10_000; i++) {
            f.add(i);
        }
        double rate = falsePositiveRate(f, 10_000, 110_000);
        assertTrue(rate < 0.001, "false positive rate was " + rate);
    }

    @Test
    void largeTargetRate() {
        BloomFilter<Integer> f = new BloomFilter<>(1_000, 0.5);
        for (int i = 0; i < 1_000; i++) {
            f.add(i);
            assertTrue(f.mightContain(i));
        }
        double rate = falsePositiveRate(f, 1_000, 101_000);
        assertTrue(rate < 0.6, "false positive rate was " + rate);
    }

    @Test
    void worksWithStringKeys() {
        BloomFilter<String> f = new BloomFilter<>(5_000, 0.01);
        for (int i = 0; i < 5_000; i++) {
            f.add("key-" + i);
        }
        for (int i = 0; i < 5_000; i++) {
            assertTrue(f.mightContain("key-" + i));
        }
        int hits = 0;
        for (int i = 0; i < 50_000; i++) {
            if (f.mightContain("other-" + i)) {
                hits++;
            }
        }
        assertTrue(hits / 50_000.0 < 0.03, "false positive rate was " + hits / 50_000.0);
    }

    @Test
    void emptyFilterContainsNothing() {
        BloomFilter<Integer> f = new BloomFilter<>(0, 0.01);
        for (int i = 0; i < 100; i++) {
            assertFalse(f.mightContain(i));
        }
    }

    @Test
    void sizingFollowsTheFormulas() {
        BloomFilter<Integer> f = new BloomFilter<>(1_000, 0.01);
        assertTrue(f.numBits() >= 9_500 && f.numBits() <= 9_700, "numBits was " + f.numBits());
        assertTrue(f.numHashes() == 6 || f.numHashes() == 7, "numHashes was " + f.numHashes());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter<Integer>(-1, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter<Integer>(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter<Integer>(10, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter<Integer>(10, -0.5));
    }

    @Test
    void sstableFilterContainsEveryKeyIncludingTombstones() {
        SSTable<Integer, String> t = new SSTable<>(
                List.of(10, 20, 30),
                List.of(Value.of("a"), Value.tombstone(), Value.of("c")));
        assertTrue(t.mightContain(10));
        assertTrue(t.mightContain(20));
        assertTrue(t.mightContain(30));
    }

    @Test
    void emptySSTableContainsNothing() {
        SSTable<Integer, String> t = new SSTable<>(List.<Integer>of(), List.<Value<String>>of());
        assertFalse(t.mightContain(1));
        assertNull(t.get(1));
    }

    @Test
    void getOnMissingKeysMostlySkipsBinarySearch() {
        List<CountingKey> keys = new ArrayList<>();
        List<Value<String>> values = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            keys.add(new CountingKey(i * 2));
            values.add(Value.of("v"));
        }
        SSTable<CountingKey, String> t = new SSTable<>(keys, values);

        CountingKey.compares = 0;
        for (int i = 0; i < 10_000; i++) {
            assertNull(t.get(new CountingKey(i * 2 + 1)));
        }
        assertTrue(CountingKey.compares < 14_000,
                "binary search ran too often: " + CountingKey.compares + " compares");
    }

    @Test
    void getStillFindsEveryKey() {
        List<CountingKey> keys = new ArrayList<>();
        List<Value<String>> values = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            keys.add(new CountingKey(i));
            values.add(Value.of("v" + i));
        }
        SSTable<CountingKey, String> t = new SSTable<>(keys, values);
        for (int i = 0; i < 1_000; i++) {
            assertEquals("v" + i, t.get(new CountingKey(i)).data());
        }
    }

    @Test
    void deletedKeysStayDeletedAcrossManyTables() {
        LSMTree<Integer, String> t = new LSMTree<>(4);
        for (int i = 0; i < 100; i++) {
            t.insert(i, "v" + i);
        }
        for (int i = 0; i < 100; i += 2) {
            t.delete(i);
        }
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 0) {
                assertNull(t.select(i), "key " + i + " was deleted");
            } else {
                assertEquals("v" + i, t.select(i));
            }
        }
    }

    static final class CountingKey implements Comparable<CountingKey> {
        static int compares = 0;
        final int value;

        CountingKey(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(CountingKey other) {
            compares++;
            return Integer.compare(value, other.value);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CountingKey c && c.value == value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }
    }
}

package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 7: range scan")
class Step7RangeScanTest {

    private final SSTable<Integer, String> table = new SSTable<>(
            List.of(10, 20, 30, 40, 50),
            List.of(Value.of("a"), Value.of("b"), Value.tombstone(), Value.of("d"), Value.of("e")));

    private static <K extends Comparable<K>, V> List<K> keys(SSTable<K, V> t) {
        List<K> out = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            out.add(t.keyAt(i));
        }
        return out;
    }

    @Test
    void sstableRangeIncludesLoAndExcludesHi() {
        assertEquals(List.of(20, 30), keys(table.range(20, 40)));
    }

    @Test
    void sstableRangeWithBoundsBetweenKeys() {
        assertEquals(List.of(20, 30, 40), keys(table.range(15, 45)));
    }

    @Test
    void sstableRangeCoveringEverything() {
        assertEquals(List.of(10, 20, 30, 40, 50), keys(table.range(0, 100)));
    }

    @Test
    void sstableRangeKeepsTombstones() {
        SSTable<Integer, String> r = table.range(25, 35);
        assertEquals(1, r.size());
        assertTrue(r.valueAt(0).isTombstone());
    }

    @Test
    void sstableRangeOutsideKeysIsEmpty() {
        assertEquals(0, table.range(0, 10).size());
        assertEquals(0, table.range(51, 99).size());
        assertEquals(0, table.range(41, 50).size());
    }

    @Test
    void sstableRangeWithLoEqualToHiIsEmpty() {
        assertEquals(0, table.range(30, 30).size());
    }

    @Test
    void sstableRangeWithLoAboveHiIsEmpty() {
        assertEquals(0, table.range(40, 20).size());
    }

    @Test
    void memtableRangeIsSortedAndFiltered() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(50, "e");
        m.put(10, "a");
        m.put(30, "c");
        m.put(20, "b");
        m.put(40, "d");
        assertEquals(List.of(20, 30, 40), keys(m.range(15, 45)));
    }

    @Test
    void memtableRangeKeepsTombstones() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(1, "a");
        m.delete(2);
        SSTable<Integer, String> r = m.range(0, 10);
        assertEquals(List.of(1, 2), keys(r));
        assertTrue(r.valueAt(1).isTombstone());
    }

    @Test
    void memtableRangeWithLoAboveHiIsEmpty() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(1, "a");
        assertEquals(0, m.range(5, 1).size());
    }

    @Test
    void memtableRangeDoesNotChangeTheMemtable() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(1, "a");
        m.put(2, "b");
        m.range(0, 10);
        assertEquals(2, m.size());
        assertEquals("a", m.get(1).data());
    }

    @Test
    void scanOnEmptyTreeIsEmpty() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        assertEquals(List.of(), t.scan(0, 100));
    }

    @Test
    void scanFromMemtableOnly() {
        LSMTree<Integer, String> t = new LSMTree<>(10);
        t.insert(3, "c");
        t.insert(1, "a");
        t.insert(2, "b");
        assertEquals(List.of(Map.entry(1, "a"), Map.entry(2, "b")), t.scan(1, 3));
    }

    @Test
    void scanExampleFromReadme() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(3, "c");
        t.insert(2, "b");
        t.insert(3, "C");
        t.delete(2);
        t.insert(5, "e");
        t.insert(4, "d");

        assertEquals(List.of(Map.entry(1, "a"), Map.entry(3, "C"), Map.entry(4, "d")), t.scan(1, 5));
        assertEquals(List.of(Map.entry(1, "a"), Map.entry(3, "C"), Map.entry(4, "d"), Map.entry(5, "e")),
                t.scan(0, 99));
        assertEquals(List.of(), t.scan(3, 3));
        assertEquals(List.of(), t.scan(6, 9));
    }

    @Test
    void tombstoneInMemtableHidesValueInSSTable() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        assertEquals(List.of(Map.entry(2, "b")), t.scan(0, 10));
    }

    @Test
    void newestValueWinsAcrossSeveralTables() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "v1");
        t.insert(100, "x");
        t.insert(1, "v2");
        t.insert(101, "x");
        t.insert(1, "v3");
        t.insert(102, "x");
        t.insert(1, "v4");
        assertEquals(List.of(Map.entry(1, "v4")), t.scan(0, 50));
    }

    @Test
    void rangeWhereEveryKeyIsDeletedIsEmpty() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        t.delete(2);
        t.insert(9, "z");
        assertEquals(List.of(), t.scan(0, 5));
        assertEquals(List.of(Map.entry(9, "z")), t.scan(0, 10));
    }

    @Test
    void scanWithLoAboveHiIsEmpty() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.insert(3, "c");
        assertEquals(List.of(), t.scan(3, 1));
    }

    @Test
    void scanWorksWithStringKeys() {
        LSMTree<String, Integer> t = new LSMTree<>(2);
        t.insert("banana", 2);
        t.insert("apple", 1);
        t.insert("cherry", 3);
        t.delete("banana");
        assertEquals(List.of(Map.entry("apple", 1), Map.entry("cherry", 3)), t.scan("a", "d"));
    }

    @Test
    void scanGivesSameResultBeforeAndAfterCompact() {
        LSMTree<Integer, String> t = new LSMTree<>(3);
        for (int i = 0; i < 40; i++) {
            t.insert(i % 13, "v" + i);
            if (i % 3 == 0) {
                t.delete((i * 5) % 13);
            }
        }
        List<Map.Entry<Integer, String>> before = t.scan(2, 11);
        t.compact();
        assertEquals(before, t.scan(2, 11));
    }

    @Test
    void scanAgreesWithSelect() {
        LSMTree<Integer, String> t = new LSMTree<>(4);
        for (int i = 0; i < 60; i++) {
            t.insert((i * 7) % 20, "v" + i);
            if (i % 5 == 0) {
                t.delete((i * 3) % 20);
            }
        }
        List<Map.Entry<Integer, String>> expected = new ArrayList<>();
        for (int k = 0; k < 20; k++) {
            String v = t.select(k);
            if (v != null) {
                expected.add(Map.entry(k, v));
            }
        }
        assertEquals(expected, t.scan(0, 20));
    }
}

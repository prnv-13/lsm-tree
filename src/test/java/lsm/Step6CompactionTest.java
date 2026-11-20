package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 6: compaction")
class Step6CompactionTest {

    // a null value means tombstone
    private static SSTable<Integer, String> sst(int[] keys, String... values) {
        List<Integer> k = new ArrayList<>();
        List<Value<String>> v = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            k.add(keys[i]);
            v.add(values[i] == null ? Value.tombstone() : Value.of(values[i]));
        }
        return new SSTable<>(k, v);
    }

    private static SSTable<Integer, String> merge(boolean dropTombstones, List<SSTable<Integer, String>> tables) {
        return Compactor.merge(tables, dropTombstones);
    }

    @Test
    void mergeOfNoTablesIsEmpty() {
        assertEquals(0, merge(false, List.of()).size());
    }

    @Test
    void mergeOfOneTableKeepsAllEntries() {
        SSTable<Integer, String> t = merge(false, List.of(sst(new int[] {1, 2, 3}, "a", "b", "c")));
        assertEquals(3, t.size());
        assertEquals("b", t.get(2).data());
    }

    @Test
    void mergedKeysAreSorted() {
        SSTable<Integer, String> t = merge(false, List.of(
                sst(new int[] {1, 4, 7}, "a", "d", "g"),
                sst(new int[] {2, 5, 8}, "b", "e", "h"),
                sst(new int[] {3, 6, 9}, "c", "f", "i")));
        assertEquals(9, t.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, t.keyAt(i));
        }
    }

    @Test
    void newerTableWinsOnDuplicateKey() {
        SSTable<Integer, String> t = merge(false, List.of(
                sst(new int[] {1, 2}, "old1", "old2"),
                sst(new int[] {1}, "mid1"),
                sst(new int[] {1, 3}, "new1", "new3")));
        assertEquals(3, t.size());
        assertEquals("new1", t.get(1).data());
        assertEquals("old2", t.get(2).data());
        assertEquals("new3", t.get(3).data());
    }

    @Test
    void olderTableDoesNotOverwriteNewer() {
        SSTable<Integer, String> t = merge(false, List.of(
                sst(new int[] {5}, "older"),
                sst(new int[] {5}, "newer")));
        assertEquals("newer", t.get(5).data());
    }

    @Test
    void keepsTombstonesWhenNotDropping() {
        SSTable<Integer, String> t = merge(false, List.of(
                sst(new int[] {1, 2}, "a", "b"),
                sst(new int[] {1}, (String) null)));
        assertEquals(2, t.size());
        assertTrue(t.get(1).isTombstone());
    }

    @Test
    void dropsTombstonesAndTheValuesTheyHideWhenAllowed() {
        SSTable<Integer, String> t = merge(true, List.of(
                sst(new int[] {1, 2}, "a", "b"),
                sst(new int[] {1, 3}, null, null)));
        assertEquals(1, t.size());
        assertNull(t.get(1));
        assertNull(t.get(3));
        assertEquals("b", t.get(2).data());
    }

    @Test
    void valueWrittenAfterTombstoneSurvivesDrop() {
        SSTable<Integer, String> t = merge(true, List.of(
                sst(new int[] {1}, "a"),
                sst(new int[] {1}, (String) null),
                sst(new int[] {1}, "back")));
        assertEquals(1, t.size());
        assertEquals("back", t.get(1).data());
    }

    @Test
    void compactOnEmptyTreeDoesNothing() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.compact();
        assertEquals(0, t.sstableCount());
    }

    @Test
    void compactLeavesOneSSTable() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        for (int i = 0; i < 10; i++) {
            t.insert(i, "v" + i);
        }
        assertEquals(5, t.sstableCount());
        t.compact();
        assertEquals(1, t.sstableCount());
        assertEquals(10, t.sstableAt(0).size());
    }

    @Test
    void compactRemovesDeletedKeys() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        t.insert(3, "c");
        t.compact();
        assertEquals(1, t.sstableCount());
        assertEquals(2, t.sstableAt(0).size());
        assertNull(t.sstableAt(0).get(1));
    }

    @Test
    void compactThatRemovesEverythingLeavesNoSSTables() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        t.delete(2);
        t.compact();
        assertEquals(0, t.sstableCount());
        assertNull(t.select(1));
        assertNull(t.select(2));
    }

    @Test
    void tombstoneInMemtableStillHidesValueAfterCompact() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        t.compact();
        assertNull(t.select(1));
        assertEquals("b", t.select(2));
    }

    @Test
    void selectGivesSameAnswersBeforeAndAfterCompact() {
        LSMTree<Integer, String> t = new LSMTree<>(3);
        for (int i = 0; i < 30; i++) {
            t.insert(i % 10, "v" + i);
            if (i % 4 == 0) {
                t.delete((i * 7) % 10);
            }
        }
        Map<Integer, String> before = new HashMap<>();
        for (int k = 0; k < 12; k++) {
            before.put(k, t.select(k));
        }
        t.compact();
        for (int k = 0; k < 12; k++) {
            assertEquals(before.get(k), t.select(k), "key " + k);
        }
    }

    @Test
    void writesAfterCompactAreNewerThanCompactedData() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "old");
        t.insert(2, "x");
        t.compact();
        t.insert(1, "new");
        t.insert(3, "y");
        assertEquals(2, t.sstableCount());
        assertEquals("new", t.select(1));
        t.delete(2);
        t.delete(4);
        assertEquals(3, t.sstableCount());
        assertNull(t.select(2));
    }
}

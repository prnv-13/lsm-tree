package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 5: LSMTree insert / delete / select")
class Step5LSMTreeTest {

    @Test
    void selectOnEmptyTreeReturnsNull() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        assertNull(t.select(1));
    }

    @Test
    void insertThenSelectFromMemtable() {
        LSMTree<Integer, String> t = new LSMTree<>(10);
        t.insert(1, "a");
        assertEquals("a", t.select(1));
    }

    @Test
    void flushHappensWhenMemtableReachesCapacity() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        assertEquals(0, t.sstableCount());
        t.insert(2, "b");
        assertEquals(1, t.sstableCount());
    }

    @Test
    void deleteCanTriggerFlush() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.delete(2);
        assertEquals(1, t.sstableCount());
    }

    @Test
    void selectFindsValueAfterItWasFlushed() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        assertEquals("a", t.select(1));
        assertEquals("b", t.select(2));
    }

    @Test
    void tombstoneInMemtableHidesValueInSSTable() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        assertNull(t.select(1));
        assertEquals("b", t.select(2));
    }

    @Test
    void tombstoneInNewerSSTableHidesValueInOlderSSTable() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        t.insert(3, "c");
        assertEquals(2, t.sstableCount());
        assertNull(t.select(1));
        assertEquals("b", t.select(2));
        assertEquals("c", t.select(3));
    }

    @Test
    void newestSSTableWinsWhenKeyIsInSeveral() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "v1");
        t.insert(100, "x");
        t.insert(1, "v2");
        t.insert(101, "x");
        t.insert(1, "v3");
        t.insert(102, "x");
        assertEquals(3, t.sstableCount());
        assertEquals("v3", t.select(1));
    }

    @Test
    void reinsertAfterDelete() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(1, "a");
        t.insert(2, "b");
        t.delete(1);
        t.insert(3, "c");
        t.insert(1, "new");
        assertEquals("new", t.select(1));
    }

    @Test
    void deleteOfNeverInsertedKey() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.delete(7);
        assertNull(t.select(7));
        t.insert(7, "now");
        assertEquals("now", t.select(7));
    }

    @Test
    void insertRejectsNullValue() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        assertThrows(NullPointerException.class, () -> t.insert(1, null));
    }

    @Test
    void worksWithStringKeysAndIntegerValues() {
        LSMTree<String, Integer> t = new LSMTree<>(2);
        t.insert("b", 2);
        t.insert("a", 1);
        t.delete("b");
        t.insert("c", 3);
        assertEquals(1, t.select("a"));
        assertNull(t.select("b"));
        assertEquals(3, t.select("c"));
    }

    @Test
    void fullExampleFromReadme() {
        LSMTree<Integer, String> t = new LSMTree<>(2);
        t.insert(5, "a");
        t.insert(3, "b");
        t.delete(5);
        assertNull(t.select(5));
        t.insert(7, "c");
        assertEquals("b", t.select(3));
        assertNull(t.select(5));
        t.insert(5, "d");
        assertEquals("d", t.select(5));
        assertNull(t.select(42));
    }
}

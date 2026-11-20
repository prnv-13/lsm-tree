package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 3: Memtable isFull / toSSTable")
class Step3FlushTest {

    @Test
    void isFullOnlyAtCapacity() {
        Memtable<Integer, String> m = new Memtable<>(2);
        assertFalse(m.isFull());
        m.put(1, "a");
        assertFalse(m.isFull());
        m.put(2, "b");
        assertTrue(m.isFull());
    }

    @Test
    void tombstoneCanFillTheMemtable() {
        Memtable<Integer, String> m = new Memtable<>(2);
        m.put(1, "a");
        m.delete(2);
        assertTrue(m.isFull());
    }

    @Test
    void toSSTableProducesSortedKeys() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(50, "e");
        m.put(10, "a");
        m.put(30, "c");
        m.put(20, "b");
        m.put(40, "d");

        SSTable<Integer, String> t = m.toSSTable();

        assertEquals(5, t.size());
        int[] expected = {10, 20, 30, 40, 50};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], t.keyAt(i));
        }
    }

    @Test
    void toSSTableKeepsValuesAlignedWithKeys() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(3, "three");
        m.put(1, "one");
        m.put(2, "two");

        SSTable<Integer, String> t = m.toSSTable();

        assertEquals("one", t.valueAt(0).data());
        assertEquals("two", t.valueAt(1).data());
        assertEquals("three", t.valueAt(2).data());
    }

    @Test
    void toSSTableKeepsTombstones() {
        Memtable<Integer, String> m = new Memtable<>(10);
        m.put(1, "a");
        m.delete(2);

        SSTable<Integer, String> t = m.toSSTable();

        assertEquals(2, t.size());
        assertEquals(2, t.keyAt(1));
        assertTrue(t.valueAt(1).isTombstone());
    }

    @Test
    void emptyMemtableGivesEmptySSTable() {
        Memtable<Integer, String> m = new Memtable<>(10);
        assertEquals(0, m.toSSTable().size());
    }
}

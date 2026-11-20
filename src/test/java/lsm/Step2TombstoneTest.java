package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 2: Memtable delete writes a tombstone")
class Step2TombstoneTest {

    private final Memtable<Integer, String> m = new Memtable<>(10);

    @Test
    void deleteStoresTombstoneInsteadOfRemoving() {
        m.put(1, "a");
        m.delete(1);
        assertTrue(m.get(1).isTombstone());
    }

    @Test
    void deleteOfUnknownKeyStillWritesTombstone() {
        m.delete(5);
        assertTrue(m.get(5).isTombstone());
    }

    @Test
    void tombstonesCountTowardSize() {
        m.put(1, "a");
        m.delete(2);
        assertEquals(2, m.size());
    }

    @Test
    void deletingTwiceDoesNotGrowSize() {
        m.delete(1);
        m.delete(1);
        assertEquals(1, m.size());
    }

    @Test
    void putAfterDeleteReplacesTombstone() {
        m.put(1, "a");
        m.delete(1);
        m.put(1, "b");
        assertFalse(m.get(1).isTombstone());
        assertEquals("b", m.get(1).data());
        assertEquals(1, m.size());
    }
}

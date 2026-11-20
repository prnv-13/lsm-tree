package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 1: Memtable put / get / size")
class Step1MemtableTest {

    private final Memtable<Integer, String> m = new Memtable<>(10);

    @Test
    void getReturnsInsertedValue() {
        m.put(1, "a");
        assertFalse(m.get(1).isTombstone());
        assertEquals("a", m.get(1).data());
    }

    @Test
    void getReturnsNullForUnknownKey() {
        assertNull(m.get(99));
    }

    @Test
    void putOverwritesExistingValue() {
        m.put(1, "a");
        m.put(1, "b");
        assertEquals("b", m.get(1).data());
    }

    @Test
    void sizeCountsDistinctKeys() {
        m.put(1, "a");
        m.put(2, "b");
        m.put(3, "c");
        assertEquals(3, m.size());
    }

    @Test
    void overwriteDoesNotGrowSize() {
        for (int i = 0; i < 5; i++) {
            m.put(1, "v" + i);
        }
        assertEquals(1, m.size());
    }
}

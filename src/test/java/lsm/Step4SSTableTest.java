package lsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Step 4: SSTable.get")
class Step4SSTableTest {

    private final SSTable<Integer, String> table = new SSTable<>(
            List.of(10, 20, 30, 40),
            List.of(Value.of("a"), Value.of("b"), Value.tombstone(), Value.of("d")));

    @Test
    void findsFirstMiddleAndLastKey() {
        assertEquals("a", table.get(10).data());
        assertEquals("b", table.get(20).data());
        assertEquals("d", table.get(40).data());
    }

    @Test
    void returnsTombstoneNotNull() {
        assertTrue(table.get(30).isTombstone());
    }

    @Test
    void returnsNullForKeyBetweenExistingKeys() {
        assertNull(table.get(25));
    }

    @Test
    void returnsNullForKeyBelowSmallest() {
        assertNull(table.get(1));
    }

    @Test
    void returnsNullForKeyAboveLargest() {
        assertNull(table.get(99));
    }

    @Test
    void emptyTableReturnsNull() {
        SSTable<Integer, String> empty = new SSTable<>(List.<Integer>of(), List.<Value<String>>of());
        assertNull(empty.get(10));
    }

    @Test
    void worksWithStringKeys() {
        SSTable<String, Integer> t = new SSTable<>(
                List.of("apple", "banana", "cherry"),
                List.of(Value.of(1), Value.of(2), Value.of(3)));
        assertEquals(2, t.get("banana").data());
        assertNull(t.get("blueberry"));
    }

    @Test
    void worksOnALargeTable() {
        int n = 10_000;
        List<Integer> keys = new ArrayList<>();
        List<Value<String>> values = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            keys.add(i * 2);
            values.add(Value.of("v" + (i * 2)));
        }
        SSTable<Integer, String> big = new SSTable<>(keys, values);

        for (int k = 0; k < n * 2; k++) {
            if (k % 2 == 0) {
                assertEquals("v" + k, big.get(k).data());
            } else {
                assertNull(big.get(k));
            }
        }
    }
}

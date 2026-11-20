package lsm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class Compactor {

    private Compactor() {}

    private record Cursor(int table, int pos) {}

    // tables are ordered oldest to newest
    public static <K extends Comparable<K>, V> SSTable<K, V> merge(
            List<SSTable<K, V>> tables, boolean dropTombstones) {

        PriorityQueue<Cursor> cursors = new PriorityQueue<>(Comparator.comparing(a -> tables.get(a.table).keyAt(a.pos)));

        List<K> keys = new ArrayList<>();
        List<Value<V>> values = new ArrayList<>();

        for (int i = tables.size() - 1; i >= 0; i--) {
            if (tables.get(i).size() == 0) continue;
            cursors.offer(new Cursor(i, 0));
        }

        while (!cursors.isEmpty()) {

            var pop = cursors.poll();
            int maxTableIndex = pop.table;

            var keyPos = pop.pos;
            var key = tables.get(maxTableIndex).keyAt(keyPos);

            var next = nextCursor(pop, tables);
            if (next != null) cursors.offer(next);

            while (!cursors.isEmpty()) {
                var peek = cursors.peek();
                if (tables.get(peek.table).keyAt(peek.pos).compareTo(key) != 0) break;
                pop = cursors.poll();
                next = nextCursor(pop, tables);
                if (next != null) cursors.offer(next);
                if (maxTableIndex < pop.table) {
                    maxTableIndex = pop.table;
                    keyPos = pop.pos;
                }
            }

            var latestVal = tables.get(maxTableIndex).valueAt(keyPos);
            if (latestVal != null && (!latestVal.isTombstone() || !dropTombstones)) {
                keys.add(key);
                values.add(latestVal);
            }
        }

        return new SSTable<>(keys, values);
    }

    private static <K extends Comparable<K>, V> Cursor nextCursor(Cursor pop, List<SSTable<K, V>> tables) {

        if (tables.get(pop.table()).size() <= pop.pos() + 1) return null;
        return new Cursor(pop.table, pop.pos() + 1);
    }
}

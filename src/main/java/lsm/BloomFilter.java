package lsm;

import org.apache.commons.codec.digest.MurmurHash3;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class BloomFilter<K> {

    private final int bitArraySize;
    private final int numHashes;
    private final BitSet bitSet;

    public BloomFilter(int expectedItems, double falsePositiveRate) {

        if (expectedItems < 0 || falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0)
            throw new IllegalArgumentException("Invalid input");

        bitArraySize = bitArraySize(expectedItems, falsePositiveRate);
        numHashes = numHashes(bitArraySize, expectedItems);
        bitSet = new BitSet(bitArraySize);
    }

    public void add(K key) {
        final var setBitIndexes = hashIndexes(key);
        for (var index : setBitIndexes) {
            bitSet.set(index);
        }
    }

    // false means definitely absent, true means maybe present
    public boolean mightContain(K key) {
        final var setBitIndexes = hashIndexes(key);
        for (var index : setBitIndexes) {
            if(!bitSet.get(index)) return false;
        }
        return true;
    }

    int numBits() {
        return bitArraySize;
    }

    int numHashes() {
        return numHashes;
    }

    private static int bitArraySize(int n, double p) {

        if (n == 0) return 1;

        double val = (-n * Math.log(p)) / (Math.log(2) * Math.log(2));
        return (int) Math.round(val);
    }

    private static int numHashes(int arraySize, int n) {

        if (n == 0) return 1;

        double val = Math.log(2) * arraySize / n;
        return Math.max(1, (int) Math.ceil(val));
    }

    private List<Integer> hashIndexes(K key) {
        byte[] array = ByteBuffer.allocate(Integer.BYTES).putInt(key.hashCode()).array();
        long[] hash = MurmurHash3.hash128x64(array);
        long h1 = hash[0];
        long h2 = hash[1];

        List<Integer> indexes = new ArrayList<>();

        for (int i = 0; i < numHashes; i++) {
            indexes.add(Math.floorMod(h1 + i * h2, bitArraySize));
        }

        return indexes;
    }
}

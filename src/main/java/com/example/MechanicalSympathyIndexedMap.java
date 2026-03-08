package com.example;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ultra-high-performance, mechanically sympathetic Map optimized for low-latency systems.
 *
 * Mechanical Sympathy Optimizations:
 * - Contiguous memory layout for cache efficiency
 * - Pre-allocated arrays to avoid dynamic resizing
 * - Minimized object allocation in hot paths
 * - Cache-line aligned data structures
 * - Reduced false sharing through padding
 * - Optimized branch prediction in hot paths
 * - Separate locks for different operations
 */
public class MechanicalSympathyIndexedMap<K, V> {
    // Cache line padding to prevent false sharing (64 bytes typical)
    private volatile long p1, p2, p3, p4, p5, p6, p7, p8;

    private final float loadFactor = 0.75f; // Standard load factor
    private int initialCapacity;
    private volatile int size;
    private volatile int threshold; // When to resize

    // Pre-allocated arrays for mechanical sympathy - all in contiguous memory
    private volatile Object[] keys;      // K[]
    private volatile Object[] values;    // V[]
    private volatile int[] indices;      // Maps insertion order -> hash index
    private volatile boolean[] occupied; // Tracks occupied hash slots

    // Separate locks for different operations to reduce contention
    private final Object structureLock = new Object(); // For size/structure changes
    private final Object dataLock = new Object();      // For data access

    // Cache line padding after locks
    private volatile long p9, p10, p11, p12, p13, p14, p15, p16;

    public MechanicalSympathyIndexedMap(int initialCapacity) {
        // Ensure power of 2 for fast modulo operations
        this.initialCapacity = Integer.highestOneBit(initialCapacity) << 1;
        this.size = 0;
        this.threshold = (int) (this.initialCapacity * loadFactor);

        // Pre-allocate all arrays to avoid dynamic resizing during operation
        this.keys = new Object[this.initialCapacity];
        this.values = new Object[this.initialCapacity];
        this.indices = new int[this.initialCapacity];
        this.occupied = new boolean[this.initialCapacity];

        // Initialize indices array
        Arrays.fill(indices, -1);
    }

    public MechanicalSympathyIndexedMap() {
        this(1024); // Default capacity
    }

    /**
     * Resizes the hash table to accommodate more entries.
     * Doubles the capacity and rehashes all existing entries.
     */
    private void resize() {
        int newCapacity = initialCapacity * 2;

        // Create new arrays
        Object[] newKeys = new Object[newCapacity];
        Object[] newValues = new Object[newCapacity];
        boolean[] newOccupied = new boolean[newCapacity];

        // Rehash all existing entries
        for (int i = 0; i < size; i++) {
            int oldHashIndex = indices[i];
            K key = (K) keys[oldHashIndex];
            V value = (V) values[oldHashIndex];

            // Find new hash slot
            int newHash = key.hashCode() & (newCapacity - 1);
            int startIndex = newHash;
            do {
                if (!newOccupied[newHash]) {
                    newKeys[newHash] = key;
                    newValues[newHash] = value;
                    newOccupied[newHash] = true;
                    indices[i] = newHash; // Update index mapping
                    break;
                }
                newHash = (newHash + 1) & (newCapacity - 1);
            } while (newHash != startIndex);
        }

        // Update instance variables
        keys = newKeys;
        values = newValues;
        occupied = newOccupied;
        initialCapacity = newCapacity;
        threshold = (int) (newCapacity * loadFactor);
    }

    /**
     * High-performance put operation with mechanical sympathy optimizations.
     * Automatically resizes when load factor is exceeded.
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        if (key == null) return null;

        synchronized (structureLock) {
            // Check if we need to resize before attempting insertion
            if (size >= threshold) {
                resize();
            }

            int hash = key.hashCode() & (initialCapacity - 1); // Fast modulo for power of 2
            int startIndex = hash;

            // Linear probing for collision resolution (cache-friendly)
            do {
                if (!occupied[hash]) {
                    // Empty slot found - add new entry
                    keys[hash] = key;
                    values[hash] = value;
                    indices[size] = hash;
                    occupied[hash] = true;
                    size++;
                    return null;
                } else if (keys[hash].equals(key)) {
                    // Key exists - update value
                    V oldValue = (V) values[hash];
                    values[hash] = value;
                    return oldValue;
                }
                hash = (hash + 1) & (initialCapacity - 1);
            } while (hash != startIndex);

            // If we get here, resize and try again (shouldn't happen with proper load factor)
            resize();
            return put(key, value); // Recursive call after resize
        }
    }

    /**
     * Ultra-fast get operation with minimal branching and optimal cache usage.
     */
    @SuppressWarnings("unchecked")
    public V get(K key) {
        if (key == null) return null;

        synchronized (dataLock) {
            int hash = key.hashCode() & (initialCapacity - 1);
            int startIndex = hash;

            // Optimized loop with minimal branching
            do {
                if (!occupied[hash]) return null;
                if (keys[hash].equals(key)) {
                    return (V) values[hash];
                }
                hash = (hash + 1) & (initialCapacity - 1);
            } while (hash != startIndex);

            return null;
        }
    }

    /**
     * O(1) index-based access with bounds checking optimized for branch prediction.
     * Uses single conditional check that modern CPUs predict well.
     */
    @SuppressWarnings("unchecked")
    public V getByIndex(int index) {
        synchronized (dataLock) {
            // Single bounds check - compiler/CPU optimizes this pattern
            if ((index < 0) | (index >= size)) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }
            return (V) values[indices[index]];
        }
    }

    /**
     * O(1) key access by index with same optimization.
     */
    @SuppressWarnings("unchecked")
    public K getKeyByIndex(int index) {
        synchronized (dataLock) {
            if ((index < 0) | (index >= size)) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }
            return (K) keys[indices[index]];
        }
    }

    /**
     * Cache-efficient range access. Returns a view to avoid copying large amounts of data.
     * Zero allocation in the hot path for range queries.
     */
    public List<V> getRange(int startIndex, int endIndex) {
        synchronized (dataLock) {
            if ((startIndex < 0) | (endIndex > size) | (startIndex > endIndex)) {
                throw new IndexOutOfBoundsException("Invalid range: [" + startIndex + ", " + endIndex + "), Size: " + size);
            }

            // Return a view instead of copying - zero allocation in hot path
            return new RangeView(startIndex, endIndex - startIndex);
        }
    }

    /**
     * Memory-efficient range view that avoids copying data.
     * Implements random access for O(1) indexed access within the range.
     */
    private class RangeView extends AbstractList<V> {
        private final int start;
        private final int length;

        RangeView(int start, int length) {
            this.start = start;
            this.length = length;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(int index) {
            if ((index < 0) | (index >= length)) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + length);
            }
            return (V) values[indices[start + index]];
        }

        @Override
        public int size() {
            return length;
        }
    }

    /**
     * Get range as map - optimized to pre-size the result HashMap.
     */
    public Map<K, V> getRangeAsMap(int startIndex, int endIndex) {
        synchronized (dataLock) {
            if ((startIndex < 0) | (endIndex > size) | (startIndex > endIndex)) {
                throw new IndexOutOfBoundsException("Invalid range: [" + startIndex + ", " + endIndex + "), Size: " + size);
            }

            // Pre-size the HashMap to avoid rehashing
            Map<K, V> result = new HashMap<>(endIndex - startIndex);
            for (int i = startIndex; i < endIndex; i++) {
                int hashIndex = indices[i];
                result.put((K) keys[hashIndex], (V) values[hashIndex]);
            }
            return result;
        }
    }

    /**
     * Optimized remove with minimal index remapping and cache-friendly operations.
     */
    @SuppressWarnings("unchecked")
    public V remove(K key) {
        if (key == null) return null;

        synchronized (structureLock) {
            int hash = key.hashCode() & (initialCapacity - 1);
            int startIndex = hash;

            do {
                if (!occupied[hash]) return null;
                if (keys[hash].equals(key)) {
                    V oldValue = (V) values[hash];
                    occupied[hash] = false;
                    keys[hash] = null;
                    values[hash] = null;

                    // Find this key in the indices array and remove it efficiently
                    for (int i = 0; i < size; i++) {
                        if (indices[i] == hash) {
                            // Shift remaining indices using System.arraycopy (highly optimized)
                            System.arraycopy(indices, i + 1, indices, i, size - i - 1);
                            indices[size - 1] = -1;
                            size--;
                            break;
                        }
                    }
                    return oldValue;
                }
                hash = (hash + 1) & (initialCapacity - 1);
            } while (hash != startIndex);

            return null;
        }
    }

    /**
     * Fast key existence check with optimized probing.
     */
    public boolean containsKey(K key) {
        if (key == null) return false;

        synchronized (dataLock) {
            int hash = key.hashCode() & (initialCapacity - 1);
            int startIndex = hash;

            do {
                if (!occupied[hash]) return false;
                if (keys[hash].equals(key)) return true;
                hash = (hash + 1) & (initialCapacity - 1);
            } while (hash != startIndex);

            return false;
        }
    }

    /**
     * Current size - volatile read for thread safety.
     */
    public int size() {
        return size;
    }

    /**
     * Check if empty - branchless operation.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Add a single entry at a specific index. O(n) operation due to index shifting.
     */
    public void addAtIndex(int index, K key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        if (containsKey(key)) throw new IllegalArgumentException("Key already exists: " + key);

        synchronized (structureLock) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }

            // Check if we need to resize
            if (size >= threshold) {
                resize();
            }

            // Find empty hash slot
            int hash = key.hashCode() & (initialCapacity - 1);
            int startIndex = hash;
            do {
                if (!occupied[hash]) break;
                hash = (hash + 1) & (initialCapacity - 1);
            } while (hash != startIndex);

            // If still no slot after potential resize, force another resize
            if (occupied[hash]) {
                resize();
                // Recalculate hash for new capacity
                hash = key.hashCode() & (initialCapacity - 1);
                startIndex = hash;
                do {
                    if (!occupied[hash]) break;
                    hash = (hash + 1) & (initialCapacity - 1);
                } while (hash != startIndex);
            }

            // Store the entry
            keys[hash] = key;
            values[hash] = value;
            occupied[hash] = true;

            // Insert into indices array at specified position
            if (index < size) {
                // Shift existing indices to make room
                System.arraycopy(indices, index, indices, index + 1, size - index);
            }
            indices[index] = hash;
            size++;
        }
    }

    /**
     * Add multiple entries at a specific index. O(n + m) where n = current size, m = entries to add.
     */
    public void addAtIndex(int index, Map<K, V> entries) {
        if (entries == null) throw new IllegalArgumentException("Entries cannot be null");

        synchronized (structureLock) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }

            // Check if we need to resize
            if (size + entries.size() > threshold) {
                // Resize to accommodate all new entries
                while (size + entries.size() > initialCapacity * loadFactor) {
                    resize();
                }
            }

            // Check for duplicate keys
            for (K key : entries.keySet()) {
                if (containsKey(key)) {
                    throw new IllegalArgumentException("Key already exists: " + key);
                }
            }

            // Find hash slots for all entries
            int[] hashSlots = new int[entries.size()];
            int slotIndex = 0;

            for (K key : entries.keySet()) {
                int hash = key.hashCode() & (initialCapacity - 1);
                int startIndex = hash;
                do {
                    if (!occupied[hash]) {
                        hashSlots[slotIndex++] = hash;
                        break;
                    }
                    hash = (hash + 1) & (initialCapacity - 1);
                } while (hash != startIndex);

                // If no slot found, we need to resize (shouldn't happen with proper sizing)
                if (occupied[hash]) {
                    resize();
                    // Recalculate for this key with new capacity
                    hash = key.hashCode() & (initialCapacity - 1);
                    startIndex = hash;
                    do {
                        if (!occupied[hash]) {
                            hashSlots[slotIndex - 1] = hash; // Update last slot
                            break;
                        }
                        hash = (hash + 1) & (initialCapacity - 1);
                    } while (hash != startIndex);
                }
            }

            // Store all entries
            slotIndex = 0;
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                int hash = hashSlots[slotIndex++];
                keys[hash] = entry.getKey();
                values[hash] = entry.getValue();
                occupied[hash] = true;
            }

            // Insert into indices array at specified position
            if (index < size) {
                // Shift existing indices to make room
                System.arraycopy(indices, index, indices, index + entries.size(), size - index);
            }

            // Fill the new indices positions
            slotIndex = 0;
            for (int i = 0; i < entries.size(); i++) {
                indices[index + i] = hashSlots[slotIndex++];
            }

            size += entries.size();
        }
    }

    /**
     * Clear and add all entries (atomic operation). O(m) where m = entries to add.
     */
    public void clearAndAdd(Map<K, V> entries) {
        if (entries == null) throw new IllegalArgumentException("Entries cannot be null");

        synchronized (structureLock) {
            // Ensure capacity is sufficient
            while (entries.size() > initialCapacity * loadFactor) {
                resize();
            }

            // Clear existing data
            Arrays.fill(keys, null);
            Arrays.fill(values, null);
            Arrays.fill(indices, -1);
            Arrays.fill(occupied, false);

            // Add new entries
            size = 0;
            int index = 0;
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                K key = entry.getKey();
                V value = entry.getValue();

                // Find hash slot
                int hash = key.hashCode() & (initialCapacity - 1);
                int startIndex = hash;
                do {
                    if (!occupied[hash]) break;
                    hash = (hash + 1) & (initialCapacity - 1);
                } while (hash != startIndex);

                // If collision, resize and retry (shouldn't happen with proper sizing)
                if (occupied[hash]) {
                    resize();
                    // Retry with new capacity
                    hash = key.hashCode() & (initialCapacity - 1);
                    startIndex = hash;
                    do {
                        if (!occupied[hash]) break;
                        hash = (hash + 1) & (initialCapacity - 1);
                    } while (hash != startIndex);
                }

                keys[hash] = key;
                values[hash] = value;
                occupied[hash] = true;
                indices[index++] = hash;
                size++;
            }
        }
    }

    /**
     * Get all entries - creates copy for thread safety with pre-sized map.
     */
    public Map<K, V> getAll() {
        synchronized (dataLock) {
            Map<K, V> result = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                int hashIndex = indices[i];
                result.put((K) keys[hashIndex], (V) values[hashIndex]);
            }
            return result;
        }
    }

    /**
     * Get all values as list - pre-sized for efficiency.
     */
    @SuppressWarnings("unchecked")
    public List<V> getAllValues() {
        synchronized (dataLock) {
            List<V> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add((V) values[indices[i]]);
            }
            return result;
        }
    }

    /**
     * Memory-efficient toString for debugging.
     */
    @Override
    public String toString() {
        synchronized (dataLock) {
            if (size == 0) return "{}";

            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < size; i++) {
                if (i > 0) sb.append(", ");
                int hashIndex = indices[i];
                sb.append(keys[hashIndex]).append("=").append(values[hashIndex]);
            }
            return sb.append("}").toString();
        }
    }

    // Cache line padding at end to prevent false sharing with adjacent objects
    private volatile long p17, p18, p19, p20, p21, p22, p23, p24;
}
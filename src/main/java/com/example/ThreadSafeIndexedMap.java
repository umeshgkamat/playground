package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A high-performance, thread-safe Map that provides both key-value and index-based access.
 * Optimized for low-latency systems with large datasets (500K+ records).
 * 
 * Uses both HashMap (O(1) key lookup) and ArrayList (O(1) index access).
 * Maintains insertion order with O(1) performance for all operations.
 */
public class ThreadSafeIndexedMap<K, V> {
    private final HashMap<K, Integer> keyToIndexMap;  // Maps key -> index
    private final ArrayList<K> indices;                 // Maintains insertion order
    private final ArrayList<V> values;                  // Maintains values in order
    private final ReentrantReadWriteLock lock;

    public ThreadSafeIndexedMap() {
        this.keyToIndexMap = new HashMap<>();
        this.indices = new ArrayList<>();
        this.values = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Put a key-value pair into the map. O(1) operation.
     */
    public V put(K key, V value) {
        lock.writeLock().lock();
        try {
            Integer existingIndex = keyToIndexMap.get(key);
            if (existingIndex != null) {
                // Key exists, just update the value
                return values.set(existingIndex, value);
            } else {
                // New key, add at end
                int newIndex = indices.size();
                keyToIndexMap.put(key, newIndex);
                indices.add(key);
                values.add(value);
                return null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get value by key. O(1) operation.
     */
    public V get(K key) {
        lock.readLock().lock();
        try {
            Integer index = keyToIndexMap.get(key);
            if (index != null) {
                return values.get(index);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get value by index (0-based). O(1) operation.
     */
    public V getByIndex(int index) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= values.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + values.size());
            }
            return values.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get key by index (0-based). O(1) operation.
     */
    public K getKeyByIndex(int index) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= indices.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + indices.size());
            }
            return indices.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get a range of entries (from startIndex inclusive to endIndex exclusive). O(n) where n = range size.
     */
    public List<V> getRange(int startIndex, int endIndex) {
        lock.readLock().lock();
        try {
            if (startIndex < 0 || endIndex > values.size() || startIndex > endIndex) {
                throw new IndexOutOfBoundsException("Invalid range: [" + startIndex + ", " + endIndex + "), Size: " + values.size());
            }
            return new ArrayList<>(values.subList(startIndex, endIndex));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get a range of entries as key-value pairs. O(n) where n = range size.
     */
    public Map<K, V> getRangeAsMap(int startIndex, int endIndex) {
        lock.readLock().lock();
        try {
            if (startIndex < 0 || endIndex > values.size() || startIndex > endIndex) {
                throw new IndexOutOfBoundsException("Invalid range: [" + startIndex + ", " + endIndex + "), Size: " + values.size());
            }
            Map<K, V> result = new LinkedHashMap<>();
            for (int i = startIndex; i < endIndex; i++) {
                result.put(indices.get(i), values.get(i));
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove a key-value pair. O(n) operation due to index remapping.
     */
    public V remove(K key) {
        lock.writeLock().lock();
        try {
            Integer removedIndex = keyToIndexMap.remove(key);
            if (removedIndex != null) {
                V removedValue = values.remove((int) removedIndex);
                indices.remove((int) removedIndex);
                
                // Remap indices for all keys after the removed index
                for (int i = removedIndex; i < indices.size(); i++) {
                    keyToIndexMap.put(indices.get(i), i);
                }
                
                return removedValue;
            }
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if key exists. O(1) operation.
     */
    public boolean containsKey(K key) {
        lock.readLock().lock();
        try {
            return keyToIndexMap.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the current size. O(1) operation.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return values.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Add a single entry at a specific index. O(n) operation.
     */
    public void addAtIndex(int index, K key, V value) {
        lock.writeLock().lock();
        try {
            if (index < 0 || index > values.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + values.size());
            }
            if (keyToIndexMap.containsKey(key)) {
                throw new IllegalArgumentException("Key already exists: " + key);
            }
            
            indices.add(index, key);
            values.add(index, value);
            keyToIndexMap.put(key, index);
            
            // Remap indices for all keys at or after the insertion point
            for (int i = index + 1; i < indices.size(); i++) {
                keyToIndexMap.put(indices.get(i), i);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Add multiple entries at a specific index. O(n + m) where m = entries to add.
     */
    public void addAtIndex(int index, Map<K, V> entries) {
        lock.writeLock().lock();
        try {
            if (index < 0 || index > values.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + values.size());
            }
            
            int currentIndex = index;
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                if (keyToIndexMap.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Key already exists: " + entry.getKey());
                }
                indices.add(currentIndex, entry.getKey());
                values.add(currentIndex, entry.getValue());
                keyToIndexMap.put(entry.getKey(), currentIndex);
                currentIndex++;
            }
            
            // Remap all indices after insertion
            for (int i = index + entries.size(); i < indices.size(); i++) {
                keyToIndexMap.put(indices.get(i), i);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Add all entries from the given map. O(n) where n = entries to add.
     */
    public void putAll(Map<K, V> entries) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                Integer existingIndex = keyToIndexMap.get(entry.getKey());
                if (existingIndex != null) {
                    values.set(existingIndex, entry.getValue());
                } else {
                    int newIndex = indices.size();
                    keyToIndexMap.put(entry.getKey(), newIndex);
                    indices.add(entry.getKey());
                    values.add(entry.getValue());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear and add (atomic operation). O(n) where n = entries to add.
     */
    public void clearAndAdd(Map<K, V> entries) {
        lock.writeLock().lock();
        try {
            keyToIndexMap.clear();
            indices.clear();
            values.clear();
            
            int index = 0;
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                keyToIndexMap.put(entry.getKey(), index);
                indices.add(entry.getKey());
                values.add(entry.getValue());
                index++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear all entries. O(1) operation.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            keyToIndexMap.clear();
            indices.clear();
            values.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get all entries as a copy (thread-safe snapshot). O(n) operation.
     */
    public Map<K, V> getAll() {
        lock.readLock().lock();
        try {
            Map<K, V> result = new LinkedHashMap<>();
            for (int i = 0; i < indices.size(); i++) {
                result.put(indices.get(i), values.get(i));
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all values as a list. O(n) operation.
     */
    public List<V> getAllValues() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(values);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all keys as a list in order. O(n) operation.
     */
    public List<K> getAllKeys() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(indices);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if map is empty. O(1) operation.
     */
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return values.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Print map contents (for debugging).
     */
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            Map<K, V> result = new LinkedHashMap<>();
            for (int i = 0; i < indices.size(); i++) {
                result.put(indices.get(i), values.get(i));
            }
            return result.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
}

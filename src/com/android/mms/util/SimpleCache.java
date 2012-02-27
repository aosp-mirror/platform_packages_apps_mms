// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.mms.util;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple cache using {@link SoftReference SoftReferences} to play well with
 * the garbage collector and an LRU cache eviction algorithm to limit the number
 * of {@link SoftReference SoftReferences}.
 * <p>
 * The interface of this class is a subset of {@link Map}.
 *
 * from Peter Balwin and books app.
 */
public class SimpleCache<K, V> {

    /**
     * A simple LRU cache to prevent the number of {@link Map.Entry} instances
     * from growing infinitely.
     */
    @SuppressWarnings("serial")
    private class SoftReferenceMap extends LinkedHashMap<K, SoftReference<V>> {

        private final int mMaxCapacity;

        public SoftReferenceMap(int initialCapacity, int maxCapacity, float loadFactor) {
            super(initialCapacity, loadFactor, true);
            mMaxCapacity = maxCapacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, SoftReference<V>> eldest) {
            return size() > mMaxCapacity;
        }
    }

    private static <V> V unwrap(SoftReference<V> ref) {
        return ref != null ? ref.get() : null;
    }

    private final SoftReferenceMap mReferences;

    /**
     * Constructor.
     *
     * @param initialCapacity the initial capacity for the {@link SoftReference}
     *            cache.
     * @param maxCapacity the maximum capacity for the {@link SoftReference}
     *            cache (this value may be large because {@link SoftReference
     *            SoftReferences} don't consume much memory compared to the
     *            larger data they typically contain).
     * @param loadFactor the initial load balancing factor for the internal
     *            {@link LinkedHashMap}
     */
    public SimpleCache(int initialCapacity, int maxCapacity, float loadFactor) {
        mReferences = new SoftReferenceMap(initialCapacity, maxCapacity, loadFactor);
    }

    /**
     * See {@link Map#get(Object)}.
     */
    public V get(Object key) {
        return unwrap(mReferences.get(key));
    }

    /**
     * See {@link Map#put(Object, Object)}.
     */
    public V put(K key, V value) {
        return unwrap(mReferences.put(key, new SoftReference<V>(value)));
    }

    /**
     * See {@link Map#clear()}.
     */
    public void clear() {
        mReferences.clear();
    }
}

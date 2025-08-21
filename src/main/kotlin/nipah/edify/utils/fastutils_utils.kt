package nipah.edify.utils

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet

inline operator fun <V> Long2ObjectOpenHashMap<V>.contains(key: Long): Boolean =
    containsKey(key)

inline operator fun <V> Long2ObjectOpenHashMap<V>.get(key: Long): V? =
    getOrDefault(key, null)

inline operator fun <V> Long2ObjectOpenHashMap<V>.set(key: Long, value: V) {
    put(key, value)
}

inline operator fun LongOpenHashSet.contains(key: Long): Boolean =
    contains(key)

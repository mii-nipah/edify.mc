package nipah.edify.collections

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class ConcurrentUniqueQueue<T>(): Iterable<T> {
    constructor(of: Collection<T>): this() {
        for (item in of) {
            add(item)
        }
    }

    private val set = ConcurrentHashMap.newKeySet<T>()
    private val queue = ConcurrentLinkedQueue<T>()
    val size get() = set.size
    val isEmpty get() = set.isEmpty()
    fun add(item: T): Boolean {
        if (set.add(item)) {
            queue.add(item)
            return true
        }
        return false
    }

    fun poll(): T? {
        val item = queue.poll() ?: return null
        set.remove(item)
        return item
    }

    fun peek(): T? {
        return queue.peek()
    }

    inline fun peekToConsume(block: (T) -> Boolean): T? {
        val item = peek() ?: return null
        if (block(item)) {
            poll()
            return item
        }
        return null
    }

    fun clear() {
        set.clear()
        queue.clear()
    }

    override fun iterator(): Iterator<T> {
        return queue.iterator()
    }
}

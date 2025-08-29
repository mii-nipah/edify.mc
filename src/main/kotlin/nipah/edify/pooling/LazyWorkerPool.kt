package nipah.edify.pooling

import nipah.edify.workers.UrWorker

data class LazyWorkerPool<T: UrWorker>(
    private val factory: () -> T,
    private val amount: Int = 10,
) {
    private val resting = ArrayDeque<T>()
    private val working = mutableListOf<T>()

    val size get() = resting.size + working.size

    val isEmpty: Boolean
        get() = resting.isEmpty() && working.isEmpty()

    fun isAvailable() = run {
        resting.isNotEmpty() || size < amount
    }

    fun borrow(): T? {
        val worker = if (resting.isNotEmpty()) {
            resting.removeLast()
        }
        else {
            if (size >= amount) {
                return null
            }
            factory()
        }
        working.add(worker)
        return worker
    }

    fun release(worker: T) {
        if (working.remove(worker)) {
            resting.add(worker)
        }
        else {
            throw IllegalArgumentException("Trying to release a worker that is not borrowed from this pool.")
        }
    }

    inline fun <R> borrowIn(block: (T) -> R): R? {
        val worker = borrow() ?: return null
        return try {
            block(worker)
        }
        finally {
            release(worker)
        }
    }
}

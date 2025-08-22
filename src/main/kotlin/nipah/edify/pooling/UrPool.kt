package nipah.edify.pooling

data class UrPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit,
    private val items: MutableList<T> = mutableListOf(),
) {
    private fun borrowRaw(): T {
        return if (items.isNotEmpty()) {
            val item = items.removeAt(items.size - 1)
            reset(item)
            item
        }
        else {
            factory()
        }
    }

    fun borrow(): UrBorrow<T> {
        val item = borrowRaw()
        return UrBorrow(item, this)
    }

    fun borrow(block: (T) -> Unit) {
        val item = borrowRaw()
        block(item)
        items.add(item)
    }

    inline fun <R> borrowIn(block: (T) -> R): R {
        val item = borrow()
        return item.use { block(item.self) }
    }

    private fun release(borrow: UrBorrow<T>) {
        val item = borrow.self
        items.add(item)
    }

    data class UrBorrow<T>(
        val self: T,
        private val pool: UrPool<T>,
        private var closed: Boolean = false,
    ): AutoCloseable {
        override fun close() {
            if (closed) return
            closed = true
            pool.release(this)
        }
    }
}

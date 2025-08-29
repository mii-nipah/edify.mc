package nipah.edify.pooling

data class LazyRingPool<T>(
    private val factory: () -> T,
    private val amount: Int = 10,
    private val reset: (T) -> Unit = { },
    private val items: MutableList<T> = mutableListOf(),
) {
    private var index = 0

    val isEmpty: Boolean
        get() = items.isEmpty()

    val canGrow: Boolean
        get() = items.size < amount

    fun any(predicate: (T) -> Boolean): Boolean {
        for (item in items) {
            if (predicate(item)) return true
        }
        return false
    }

    fun borrow(): T {
        if (items.size < amount) {
            items.add(factory())
        }
        val item = items[index]
        index = (index + 1) % amount
        reset(item)
        return item
    }

    inline fun borrow(block: (T) -> Unit) {
        val item = borrow()
        block(item)
    }

    inline fun <R> borrowIn(block: (T) -> R): R {
        val item = borrow()
        return block(item)
    }
}

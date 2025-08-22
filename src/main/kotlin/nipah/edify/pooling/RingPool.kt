package nipah.edify.pooling

data class RingPool<T>(
    private val factory: () -> T,
    private val amount: Int = 10,
    private val reset: (T) -> Unit = { },
    private val items: MutableList<T> = MutableList(amount) { factory() },
) {
    private var index = 0

    fun borrow(): T {
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

package nipah.edify.utils

import java.util.concurrent.CopyOnWriteArrayList

fun <T> Collection<T>.toCopyOnWriteArrayList(): CopyOnWriteArrayList<T> {
    return CopyOnWriteArrayList(this)
}

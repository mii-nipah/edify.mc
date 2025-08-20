package nipah.edify.utils

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

fun <T> Collection<T>.toCopyOnWriteArrayList(): CopyOnWriteArrayList<T> {
    return CopyOnWriteArrayList(this)
}

fun <T> List<T>.takeRandomNPercentile(
    percentile: Float,
    random: Random = Random,
): List<T> {
    if (percentile <= 0f || percentile > 1f) {
        throw IllegalArgumentException("Percentile must be in the range (0, 1]")
    }
    val n = (size * percentile).toInt()
    return if (n >= size) {
        this
    }
    else {
        this.shuffled(random).take(n)
    }
}

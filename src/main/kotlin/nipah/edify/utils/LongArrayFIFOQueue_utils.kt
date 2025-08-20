package nipah.edify.utils

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue


inline fun LongArrayFIFOQueue.isNotEmpty(): Boolean {
    return this.isEmpty.not()
}

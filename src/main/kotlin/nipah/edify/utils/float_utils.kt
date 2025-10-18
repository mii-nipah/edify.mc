package nipah.edify.utils

inline fun Float.coerceNaN(to: Float = 0f): Float {
    return if (this.isNaN()) to else this
}

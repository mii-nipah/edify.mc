package nipah.edify.types

@JvmInline
value class Half(val f: Float) {
    companion object {
        val MIN_VALUE = (-65504f).half
        val MAX_VALUE = 65504f.half
        val ZERO = 0f.half

        inline fun of(value: Float) = Half(value.coerceIn(-65504f, 65504f))
    }
}

inline fun Half.toFloat() = this.f
inline val Float.half get() = Half.of(this)

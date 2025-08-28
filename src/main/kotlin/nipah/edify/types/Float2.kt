package nipah.edify.types

@JvmInline
value class Float2(val bits: Long) {
    val x: Float get() = Float.fromBits((bits ushr 32).toInt())
    val y: Float get() = Float.fromBits(bits.toInt())

    companion object {
        fun of(x: Float, y: Float): Float2 {
            val hi = x.toRawBits().toLong() shl 32
            val lo = y.toRawBits().toLong() and 0xFFFF_FFFFL
            return Float2(hi or lo)
        }
    }
}

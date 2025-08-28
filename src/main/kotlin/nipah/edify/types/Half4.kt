package nipah.edify.types

@JvmInline
value class Half4(val bits: Long) {

    // ---- lane extractors (binary16 -> Float) ----
    val x: Float get() = halfToFloat(((bits ushr 48) and 0xFFFF).toShort())
    val y: Float get() = halfToFloat(((bits ushr 32) and 0xFFFF).toShort())
    val z: Float get() = halfToFloat(((bits ushr 16) and 0xFFFF).toShort())
    val w: Float get() = halfToFloat((bits and 0xFFFF).toShort())

    override fun toString(): String {
        return "Half4(${x}, ${y}, ${z}, ${w})"
    }

    // ---- pair extractors (two lanes -> Float32 via raw bits) ----
    val xy: Float
        get() {
            val hi = ((bits ushr 48).toInt() and 0xFFFF)
            val lo = ((bits ushr 32).toInt() and 0xFFFF)
            return Float.fromBits((hi shl 16) or lo)
        }
    val zw: Float
        get() {
            val hi = ((bits ushr 16).toInt() and 0xFFFF)
            val lo = (bits.toInt() and 0xFFFF)
            return Float.fromBits((hi shl 16) or lo)
        }

    companion object {
        // ---- constructors ----

        // four binary16 values (Float inputs are converted to half)
        fun of(x: Float, y: Float, z: Float, w: Float): Half4 =
            Half4(pack(f32toF16(x), f32toF16(y), f32toF16(z), f32toF16(w)))

        // two full Float32 values (split across xy and zw)
        fun of(xy: Float, zw: Float): Half4 {
            val a = xy.toRawBits()
            val b = zw.toRawBits()
            val x = (a ushr 16) and 0xFFFF
            val y = a and 0xFFFF
            val z = (b ushr 16) and 0xFFFF
            val w = b and 0xFFFF
            return Half4(pack(x, y, z, w))
        }

        // first pair is Float32; last two are binary16
        fun of(xy: Float, z: Half, w: Half): Half4 {
            val a = xy.toRawBits()
            val x = (a ushr 16) and 0xFFFF
            val y = a and 0xFFFF
            return Half4(pack(x, y, f32toF16(z.toFloat()), f32toF16(w.toFloat())))
        }

        // first two are binary16; last pair is Float32
        fun of(x: Half, y: Half, zw: Float): Half4 {
            val b = zw.toRawBits()
            val z = (b ushr 16) and 0xFFFF
            val w = b and 0xFFFF
            return Half4(pack(f32toF16(x.toFloat()), f32toF16(y.toFloat()), z, w))
        }

        // ---- packing ----
        private fun pack(x: Int, y: Int, z: Int, w: Int): Long =
            ((x and 0xFFFF).toLong() shl 48) or
                    ((y and 0xFFFF).toLong() shl 32) or
                    ((z and 0xFFFF).toLong() shl 16) or
                    ((w and 0xFFFF).toLong())

        // ---- binary16 <-> Float32 converters (IEEE-754, round-to-nearest-even) ----

        private fun f32toF16(f: Float): Int {
            val fbits = f.toRawBits()
            val sign = (fbits ushr 16) and 0x8000
            var valRounded = (fbits and 0x7fffffff) + 0x1000 // add rounding bias (for >>13)
            // Overflow: >= 0x47800000 -> Inf/NaN handling
            if (valRounded >= 0x47800000) {
                if ((fbits and 0x7fffffff) >= 0x47800000) {
                    // Inf or NaN in float32 domain
                    return sign or 0x7c00 or (((fbits and 0x7fffff) ushr 13).let {
                        // keep NaN payload; ensure at least 1 bit for NaN
                        if (it == 0 && (fbits and 0x7fffff) != 0) 1 else it
                    })
                }
                // Largest finite half (0x7bff)
                return sign or 0x7bff
            }
            // Normalized half
            if (valRounded >= 0x38800000) {
                return sign or ((valRounded - 0x38000000) ushr 13)
            }
            // Too small for normalized; might be subnormal or zero
            if (valRounded < 0x33000000) {
                // underflow to zero
                return sign
            }
            // Subnormal half
            val exp = ((fbits ushr 23) and 0xff)
            val mant = (fbits and 0x7fffff) or 0x800000 // add implicit 1
            // shift so that exponent becomes 0 in half: shift = 126 - exp (plus 1 for mant align -> 14 + (113-exp))
            val shift = 126 - exp
            val sub = (mant + (0x800000 ushr (shift))) ushr (shift + 1) // rounded
            return sign or (sub ushr 13)
        }

        private fun halfToFloat(h: Short): Float {
            val bits = h.toInt() and 0xFFFF
            val sign = (bits and 0x8000) shl 16
            val exp = (bits ushr 10) and 0x1F
            val mant = bits and 0x03FF
            val out = when (exp) {
                0 -> { // zero or subnormal
                    if (mant == 0) {
                        sign
                    }
                    else {
                        // normalize subnormal
                        var m = mant
                        var e = -1
                        while ((m and 0x0400) == 0) {
                            m = m shl 1; e--
                        }
                        m = m and 0x03FF
                        val e32 = (e + 1) + (127 - 15)
                        sign or (e32 shl 23) or (m shl 13)
                    }
                }

                0x1F -> { // Inf / NaN
                    sign or 0x7F800000 or (mant shl 13)
                }

                else -> { // normalized
                    val e32 = exp + (127 - 15)
                    sign or (e32 shl 23) or (mant shl 13)
                }
            }
            return Float.fromBits(out)
        }
    }
}

package nipah.edify.utils

import org.joml.Matrix3f
import kotlin.math.abs

fun Matrix3f.absolute(): Matrix3f {
    this.m00 = abs(this.m00); this.m01 = abs(this.m01); this.m02 = abs(this.m02)
    this.m10 = abs(this.m10); this.m11 = abs(this.m11); this.m12 = abs(this.m12)
    this.m20 = abs(this.m20); this.m21 = abs(this.m21); this.m22 = abs(this.m22)
    return this
}

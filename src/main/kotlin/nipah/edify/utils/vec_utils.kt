package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

fun Vec3.toVec3i(): Vec3i {
    return Vec3i(this.x.toInt(), this.y.toInt(), this.z.toInt())
}

operator fun Vec3.minus(other: Vec3): Vec3 {
    return Vec3(this.x - other.x, this.y - other.y, this.z - other.z)
}

operator fun Vec3.plus(other: Vec3): Vec3 {
    return Vec3(this.x + other.x, this.y + other.y, this.z + other.z)
}

operator fun Vec3i.minus(other: Vec3i): Vec3i {
    return Vec3i(this.x - other.x, this.y - other.y, this.z - other.z)
}

operator fun Vec3i.plus(other: Vec3i): Vec3i {
    return Vec3i(this.x + other.x, this.y + other.y, this.z + other.z)
}

fun BlockPos.toVec3f(): Vector3f {
    return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
}

fun BlockPos.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun BlockPos.toVec3i(): Vec3i {
    return Vec3i(this.x, this.y, this.z)
}

fun Vector3f.toVec3i(): Vec3i {
    return Vec3i(this.x.toInt(), this.y.toInt(), this.z.toInt())
}

fun Vector3f.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

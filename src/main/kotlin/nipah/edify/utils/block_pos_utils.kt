package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i

operator fun Vec3i.component1() = this.x
operator fun Vec3i.component2() = this.y
operator fun Vec3i.component3() = this.z

fun BlockPos.MutableBlockPos.minAssign(other: BlockPos) {
    if (other.x < this.x) this.x = other.x
    if (other.y < this.y) this.y = other.y
    if (other.z < this.z) this.z = other.z
}

fun BlockPos.MutableBlockPos.maxAssign(other: BlockPos) {
    if (other.x > this.x) this.x = other.x
    if (other.y > this.y) this.y = other.y
    if (other.z > this.z) this.z = other.z
}

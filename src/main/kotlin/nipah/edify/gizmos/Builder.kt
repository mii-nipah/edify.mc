package nipah.edify.gizmos

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class Builder(private val tag: String) {
    fun line(a: Vec3, b: Vec3, color: Int, depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0) =
        Gizmos.line(a, b, color, depth, ttl, tag)

    fun polyline(points: List<Vec3>, color: Int, closed: Boolean = false,
                 depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0) =
        Gizmos.polyline(points, color, closed, depth, ttl, tag)

    fun box(aabb: AABB, color: Int, depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0) =
        Gizmos.box(aabb, color, depth, ttl, tag)

    fun circle(center: Vec3, normal: Vec3, radius: Double, segments: Int = 32,
               color: Int, depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0) =
        Gizmos.circle(center, normal, radius, segments, color, depth, ttl, tag)

    fun text(text: String, pos: Vec3, argb: Int, seeThrough: Boolean = false,
             scale: Float = 1.0f, ttl: Int = 0) =
        Gizmos.text(text, pos, argb, seeThrough, scale, ttl, tag)

    fun axes(origin: Vec3, size: Double = 1.0, ttl: Int = 0) =
        Gizmos.axes(origin, size, ttl, tag)
}

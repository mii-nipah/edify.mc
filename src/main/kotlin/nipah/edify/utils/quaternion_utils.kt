package nipah.edify.utils

import org.joml.Math
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp

// Returns a NEW quaternion = incremental world-space tilt applied to 'q'.
// 'q' is your current world orientation (no matrix math needed here).
fun Quaternionf.tiltTowardCoM(
    comWorld: Vector3f,      // center of mass (world)
    pivotWorld: Vector3f,    // pivot/foot (world)
    dt: Float = 1f / 20,               // seconds since last update
    lambda: Float = 6f,      // responsiveness (higher = faster)
    maxStepRad: Float = (15f * Math.PI / 180.0).toFloat(), // per-frame cap
): Quaternionf {
    val q = this
    // 1) Current "up" in world space from the quaternion
    val up = Vector3f(0f, 1f, 0f)
    q.transform(up).normalize()

    // 2) Target direction (world)
    val b = Vector3f(comWorld).sub(pivotWorld).normalize()
    if (!b.isFinite || !up.isFinite) return Quaternionf(q)

    // 3) Shortest-arc rotation a->b (axis=cross, angle=atan2(|cross|, dot))
    val axis = up.cross(b, Vector3f())
    val s = axis.length() // = sin(theta)
    val c = up.dot(b).coerceIn(-1f, 1f)

    if (s < 1e-6f) {
        if (c < 0f) {
            // 180°: pick stable perpendicular axis
            perpendicularTo(up, axis).normalize()
        }
        else {
            // Already aligned
            return Quaternionf(q)
        }
    }
    else {
        axis.div(s) // normalize
    }

    val fullAngle = atan2(s.toDouble(), c.toDouble()).toFloat()

    // 4) Exponential, framerate-independent step
    val t = (1f - exp(-lambda * dt)).coerceIn(0f, 1f)
    val step = (fullAngle * t).coerceIn(0f, maxStepRad)
    if (step <= 0f) return Quaternionf(q)

    // 5) Build incremental WORLD-space rotation and pre-multiply
    val qStep = Quaternionf().rotationAxis(step, axis.x, axis.y, axis.z)
    return Quaternionf(qStep).mul(q) // world tilt first, then current orientation
}

// ——— Utilities ———
private fun perpendicularTo(v: Vector3f, out: Vector3f = Vector3f()): Vector3f =
    if (abs(v.y) < 0.9f) out.set(0f, 1f, 0f).cross(v) else out.set(1f, 0f, 0f).cross(v)

private fun Vector3f.isFinite() =
    x.isFinite() && y.isFinite() && z.isFinite()

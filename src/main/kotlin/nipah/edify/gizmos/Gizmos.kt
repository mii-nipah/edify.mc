package nipah.edify.gizmos

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Depth { DEPTH_TEST, XRAY }

object Gizmos {
    object Color {
        const val red = 0xFFFF5555.toInt()
        const val green = 0xFF55FF55.toInt()
        const val blue = 0xFF5555FF.toInt()
        const val yellow = 0xFFFFFF55.toInt()
        const val cyan = 0xFF55FFFF.toInt()
        const val magenta = 0xFFFF55FF.toInt()
        const val white = 0xFFFFFFFF.toInt()
        const val black = 0xFF000000.toInt()
        const val gray = 0xFF888888.toInt()
        const val lightGray = 0xFFCCCCCC.toInt()
        const val darkGray = 0xFF444444.toInt()
        const val orange = 0xFFFFA500.toInt()
        const val purple = 0xFF800080.toInt()
        const val pink = 0xFFFFC0CB.toInt()
        const val brown = 0xFFA52A2A.toInt()

        fun lerp(c1: Int, c2: Int, t: Float): Int {
            val a1 = (c1 ushr 24) and 0xFF
            val r1 = (c1 ushr 16) and 0xFF
            val g1 = (c1 ushr 8) and 0xFF
            val b1 = c1 and 0xFF

            val a2 = (c2 ushr 24) and 0xFF
            val r2 = (c2 ushr 16) and 0xFF
            val g2 = (c2 ushr 8) and 0xFF
            val b2 = c2 and 0xFF

            val a = (a1 + ((a2 - a1) * t)).toInt().coerceIn(0, 255)
            val r = (r1 + ((r2 - r1) * t)).toInt().coerceIn(0, 255)
            val g = (g1 + ((g2 - g1) * t)).toInt().coerceIn(0, 255)
            val b = (b1 + ((b2 - b1) * t)).toInt().coerceIn(0, 255)

            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

    }

    // -------- Public API --------
    fun line(a: Vec3, b: Vec3, color: Int, depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0, tag: String? = null) {
        push(LineCmd(a, b, color, depth, ttl, tag))
    }

    fun polyline(
        points: List<Vec3>, color: Int, closed: Boolean = false,
        depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0, tag: String? = null,
    ) {
        if (points.size < 2) return
        push(PolylineCmd(points.toList(), closed, color, depth, ttl, tag))
    }

    fun box(aabb: AABB, color: Int, depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0, tag: String? = null) {
        push(BoxCmd(aabb, color, depth, ttl, tag))
    }

    fun block(
        pos: BlockPos,
        color: Int,
        depth: Depth = Depth.DEPTH_TEST,
        ttl: Int = 0,
        tag: String? = null,
        inflate: Double = 0.03,
    ) {
        val aabb = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        ).inflate(inflate)
        push(BoxCmd(aabb, color, depth, ttl, tag))
    }

    fun blockFill(
        pos: BlockPos,
        color: Int,
        depth: Depth = Depth.DEPTH_TEST,
        ttl: Int = 0,
        tag: String? = null,
        inflate: Double = 0.03,
    ) {
        val aabb = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        ).inflate(inflate)
        push(BoxFillCmd(aabb, color, depth, ttl, tag))
    }

    fun circle(
        center: Vec3, normal: Vec3, radius: Double, segments: Int = 32,
        color: Int, depth: Depth = Depth.DEPTH_TEST, ttl: Int = 0, tag: String? = null,
    ) {
        val (u, v) = orthonormalBasis(normal)
        val pts = ArrayList<Vec3>(segments + 1)
        for (i in 0..segments) {
            val t = (i.toDouble() / segments) * (Math.PI * 2.0)
            val offset = u.scale(cos(t) * radius).add(v.scale(sin(t) * radius))
            pts += center.add(offset)
        }
        polyline(pts, color, closed = true, depth = depth, ttl = ttl, tag = tag)
    }

    fun text(
        text: String, pos: Vec3, argb: Int, seeThrough: Boolean = false,
        scale: Float = 1.0f, ttl: Int = 0, tag: String? = null,
    ) {
        push(TextCmd(text, pos, argb, seeThrough, scale, ttl, tag))
    }

    fun axes(origin: Vec3, size: Double = 1.0, ttl: Int = 0, tag: String? = null) {
        line(origin, origin.add(size, 0.0, 0.0), 0xFFFF5555.toInt(), ttl = ttl, tag = tag) // X red
        line(origin, origin.add(0.0, size, 0.0), 0xFF55FF55.toInt(), ttl = ttl, tag = tag) // Y green
        line(origin, origin.add(0.0, 0.0, size), 0xFF5555FF.toInt(), ttl = ttl, tag = tag) // Z blue
    }

    fun tag(tag: String, block: Builder.() -> Unit) {
        Builder(tag).block()
    }

    fun clear(tag: String? = null) {
        if (tag == null) commands.clear() else commands.removeIf { it.tag == tag }
    }

    // -------- Rendering entrypoints (call from your event glue) --------
    internal fun render(stagePose: PoseStack, cameraPos: Vec3) {
        if (commands.isEmpty()) return
        val mc = Minecraft.getInstance()
        val buffers = mc.renderBuffers().bufferSource()

        // world-space transform
        stagePose.pushPose()
        stagePose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        // LINES depth-tested
        val linesVC = buffers.getBuffer(RenderType.lines())
        commands.forEach { it.renderLines(stagePose, linesVC, depthTest = true) }
        buffers.endBatch(RenderType.lines())

        // LINES x-ray (no depth)
        val xrayVC = buffers.getBuffer(GizmoRenderTypes.linesNoDepth())
        commands.forEach { it.renderLines(stagePose, xrayVC, depthTest = false) }
        buffers.endBatch(GizmoRenderTypes.linesNoDepth())

        // FILLED x-ray (no depth)
        val filledXrayVC = buffers.getBuffer(GizmoRenderTypes.filledNoDepth())
        commands.forEach { it.renderFilled(stagePose, filledXrayVC, depthTest = false) }
        buffers.endBatch(GizmoRenderTypes.filledNoDepth())

        // FILLED depth-tested
        val filledVC = buffers.getBuffer(GizmoRenderTypes.filledWithDepth())
        commands.forEach { it.renderFilled(stagePose, filledVC, depthTest = true) }
        buffers.endBatch(GizmoRenderTypes.filledWithDepth())

        stagePose.popPose()

        // world-text (batched separately)
        commands.forEach { it.renderText(stagePose, buffers, cameraPos) }
        // Let MC flush the remaining types at frame end; or call buffers.endBatch() if you prefer.
    }

    internal fun tick() {
        if (commands.isEmpty()) return
        commands.removeIf { it.tickAndIsDead() }
    }

    internal fun reset() {
        commands.clear()
    }

    // -------- Internals --------
    private val commands = CopyOnWriteArrayList<Cmd>()

    private fun push(c: Cmd) {
        commands += c
    }

    private fun orthonormalBasis(n: Vec3): Pair<Vec3, Vec3> {
        val nn = if (n.lengthSqr() == 0.0) Vec3(0.0, 1.0, 0.0) else n.normalize()
        val a = if (kotlin.math.abs(nn.y) < 0.99) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val u = nn.cross(a).normalize()
        val v = nn.cross(u).normalize()
        return u to v
    }

    // -------- Command types --------
    private sealed interface Cmd {
        var ttl: Int
        val tag: String?
        fun tickAndIsDead(): Boolean {
            if (ttl < 0) return false // persistent
            ttl -= 1
            return ttl < 0
        }

        fun renderFilled(pose: PoseStack, vc: VertexConsumer, depthTest: Boolean) {}

        fun renderLines(pose: PoseStack, vc: VertexConsumer, depthTest: Boolean) {}
        fun renderText(pose: PoseStack, buffers: net.minecraft.client.renderer.MultiBufferSource, cam: Vec3) {}
    }

    private data class LineCmd(
        val a: Vec3, val b: Vec3, val color: Int, val depth: Depth, override var ttl: Int, override val tag: String?,
    ): Cmd {
        override fun renderLines(pose: PoseStack, vc: VertexConsumer, depthTest: Boolean) {
            if ((depth == Depth.DEPTH_TEST) != depthTest) return
            val (cR, cG, cB, cA) = ColorUtil.rgba(color)
            val m = pose.last().pose()
            vc.addVertex(
                m, a.x.toFloat(), a.y.toFloat(), a.z.toFloat()
            ).setColor(cR, cG, cB, cA).setNormal(0f, 1f, 0f)
            vc.addVertex(
                m, b.x.toFloat(), b.y.toFloat(), b.z.toFloat()
            ).setColor(cR, cG, cB, cA).setNormal(0f, 1f, 0f)
        }
    }

    private data class PolylineCmd(
        val pts: List<Vec3>,
        val closed: Boolean,
        val color: Int,
        val depth: Depth,
        override var ttl: Int,
        override val tag: String?,
    ): Cmd {
        override fun renderLines(pose: PoseStack, vc: VertexConsumer, depthTest: Boolean) {
            if ((depth == Depth.DEPTH_TEST) != depthTest) return
            val (cR, cG, cB, cA) = ColorUtil.rgba(color)
            val m = pose.last().pose()
            for (i in 0 until pts.size - 1) {
                val p = pts[i]
                val q = pts[i + 1]
                vc.addVertex(
                    m, p.x.toFloat(), p.y.toFloat(), p.z.toFloat()
                ).setColor(cR, cG, cB, cA)
                    .setNormal(0f, 1f, 0f)
                vc.addVertex(
                    m, q.x.toFloat(), q.y.toFloat(), q.z.toFloat()
                ).setColor(cR, cG, cB, cA)
                    .setNormal(0f, 1f, 0f)
            }
            if (closed) {
                val p = pts.last()
                val q = pts.first()
                vc.addVertex(
                    m, p.x.toFloat(), p.y.toFloat(), p.z.toFloat()
                ).setColor(cR, cG, cB, cA)
                    .setNormal(0f, 1f, 0f)
                vc.addVertex(
                    m, q.x.toFloat(), q.y.toFloat(), q.z.toFloat()
                ).setColor(cR, cG, cB, cA)
                    .setNormal(0f, 1f, 0f)
            }
        }
    }

    private data class BoxCmd(
        val box: AABB, val color: Int, val depth: Depth, override var ttl: Int, override val tag: String?,
    ): Cmd {
        override fun renderLines(pose: PoseStack, vc: VertexConsumer, depthTest: Boolean) {
            if ((depth == Depth.DEPTH_TEST) != depthTest) return
            val (r, g, b, a) = ColorUtil.rgba(color)
            LevelRenderer.renderLineBox(pose, vc, box, r, g, b, a)
        }
    }

    private data class BoxFillCmd(
        val box: AABB, val color: Int, val depth: Depth, override var ttl: Int, override val tag: String?,
    ): Cmd {
        override fun renderFilled(pose: PoseStack, vc: VertexConsumer, depthTest: Boolean) {
            if ((depth == Depth.DEPTH_TEST) != depthTest) return
            val (r, g, b, a) = ColorUtil.rgba(color)
            val eps = 1.0 / 512.0
            val x0 = box.minX + eps
            val y0 = box.minY + eps
            val z0 = box.minZ + eps
            val x1 = box.maxX - eps
            val y1 = box.maxY - eps
            val z1 = box.maxZ - eps

            LevelRenderer.addChainedFilledBoxVertices(
                pose, vc, x0, y0, z0, x1, y1, z1, r, g, b, a
            )
        }
    }

    private data class TextCmd(
        val text: String, val pos: Vec3, val argb: Int, val seeThrough: Boolean, val scale: Float,
        override var ttl: Int, override val tag: String?,
    ): Cmd {
        override fun renderText(pose: PoseStack, buffers: net.minecraft.client.renderer.MultiBufferSource, cam: Vec3) {
            val mc = Minecraft.getInstance()
            val font: Font = mc.font
            pose.pushPose()
            // camera-relative
            pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z)
            // face camera
            pose.mulPose(mc.gameRenderer.mainCamera.rotation())
            val s = (max(0.001f, scale)) * 0.025f
            pose.scale(-s, -s, s)
            val display = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
            font.drawInBatch(
                text, 0f, 0f, argb, false, pose.last().pose(), buffers, display,
                0, 0x00F000F0 // full-bright-ish
            )
            pose.popPose()
        }
    }
}

private object ColorUtil {
    // ARGB int -> normalized RGBA floats expected by vertices
    fun rgba(argb: Int): FloatArray {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        // clamp just in case
        return floatArrayOf(clamp(r), clamp(g), clamp(b), clamp(a))
    }

    private fun clamp(x: Float) = min(1f, max(0f, x))
}

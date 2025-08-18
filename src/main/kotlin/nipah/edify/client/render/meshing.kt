package nipah.edify.client.render

import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.BlockRenderDispatcher
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState

fun buildSolidMesh(
    level: BlockAndTintGetter,
    blocks: List<BlockPos>,
    origin: BlockPos = blocks.first(),
): VertexBuffer {
    val mc = Minecraft.getInstance()
    val dispatcher: BlockRenderDispatcher = mc.blockRenderer
    val pose = PoseStack()

    // The buffer must match the RenderType's vertex format for solid blocks
    val bBuf = ByteBufferBuilder(64_000)
    val buf = BufferBuilder(bBuf, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)

    val random: RandomSource = RandomSource.create(42L)

    for (pos in blocks) {
        val state: BlockState = level.getBlockState(pos)
        if (state.renderShape != RenderShape.MODEL) continue
        // keep minimal: only draw things that belong to the solid layer
        if (!state.canOcclude()) continue

        val baked = dispatcher.getBlockModel(state)
        pose.pushPose()
        // bake relative to origin, but sample lighting/ao with world pos
        pose.translate((pos.x - origin.x).toDouble(), (pos.y - origin.y).toDouble(), (pos.z - origin.z).toDouble())

        // ModelBlockRenderer -> writes directly into BufferBuilder (it is a VertexConsumer)
        dispatcher.modelRenderer.tesselateBlock(
            level, baked, state, pos, pose, buf,
            true, random, state.getSeed(pos), 0
        )
        pose.popPose()
    }

    val rendered = buf.build() ?: error("Empty mesh")
    val vbo = VertexBuffer(VertexBuffer.Usage.STATIC)
    vbo.bind()
    vbo.upload(rendered)
    vbo.close()
    VertexBuffer.unbind()
    return vbo
}

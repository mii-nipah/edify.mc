package nipah.edify.client.render

import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.BlockRenderDispatcher
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.types.WorldBlock
import nipah.edify.types.toBlockPosSet

fun isNonRenderableMesh(
    state: BlockState,
): Boolean {
    if (state.renderShape != RenderShape.MODEL) return true
    val rt = ItemBlockRenderTypes.getChunkRenderType(state)
    return rt == RenderType.translucent()
}

fun buildSolidMesh(
    level: BlockAndTintGetter,
    blocks: List<WorldBlock>,
    origin: BlockPos = blocks.first().pos,
): Pair<VertexBuffer, List<WorldBlock>>? {
    val nonRenderable = mutableListOf<WorldBlock>(
        *blocks.mapNotNull {
            val state = it.state
            if (isNonRenderableMesh(state)) it
            else if (state.renderShape != RenderShape.MODEL) it
            else null
        }.toTypedArray()
    )
    if (nonRenderable.size == blocks.size) {
        return null
    }
    val nonRenderableSet = nonRenderable.toBlockPosSet()

    val mc = Minecraft.getInstance()
    val dispatcher: BlockRenderDispatcher = mc.blockRenderer
    val pose = PoseStack()


    // The buffer must match the RenderType's vertex format for solid blocks
    val bBuf = ByteBufferBuilder(64_000)
    val buf = BufferBuilder(bBuf, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)

    val random: RandomSource = RandomSource.create(42L)

    for ((pos, state) in blocks) {
        if (pos in nonRenderableSet) continue

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

    val rendered = buf.build() ?: return null
    val vbo = VertexBuffer(VertexBuffer.Usage.STATIC)
    vbo.bind()
    vbo.upload(rendered)
    VertexBuffer.unbind()
    return vbo to nonRenderable
}

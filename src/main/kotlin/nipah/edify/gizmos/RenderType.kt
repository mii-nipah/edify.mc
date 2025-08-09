package nipah.edify.gizmos

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import java.util.OptionalDouble

object GizmoRenderTypes {
    private val LINES_NO_DEPTH: RenderType = RenderType.create(
        "gizmo_lines_no_depth",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES, 256, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_LINES_SHADER)
            .setLineState(RenderStateShard.LineStateShard(OptionalDouble.of(1.0)))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false)
    )
    fun linesNoDepth(): RenderType = LINES_NO_DEPTH
}

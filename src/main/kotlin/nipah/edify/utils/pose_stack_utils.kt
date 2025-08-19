package nipah.edify.utils

import com.mojang.blaze3d.vertex.PoseStack

inline fun PoseStack.withPush(
    block: () -> Unit,
): PoseStack {
    this.pushPose()
    try {
        block()
    }
    finally {
        this.popPose()
    }
    return this
}

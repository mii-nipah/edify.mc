package nipah.edify.utils

import net.minecraft.world.level.Level
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime

fun Level.preventNextUniversalEventFromRemovingBlock() {
    Level_AnyBlockRemovedMixinRuntime.preventPostingNext()
}

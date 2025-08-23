// src/main/java/your/mod/mixin/Level_AnyBlockRemovedMixin.java
package nipah.edify.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class Level_AnyBlockRemovedMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void anyblock$fireIfRemoved(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;                  // nothing changed -> ignore
        Level self = (Level) (Object) this;
        if (self.isClientSide) return;                      // server only
        if (newState.isAir()) {
            Level_AnyBlockRemovedMixinRuntime.INSTANCE.onAnyBlockRemoved(
                    (ServerLevel) self, pos
            );
        }
    }
}

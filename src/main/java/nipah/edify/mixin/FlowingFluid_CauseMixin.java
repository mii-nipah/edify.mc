package nipah.edify.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FlowingFluid.class)
public class FlowingFluid_CauseMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private static boolean wrapFallSet(Level level, BlockPos pos, BlockState state, int flags,
                                       Operation<Boolean> op) {
        Level_AnyBlockRemovedMixinRuntime.INSTANCE.preventPostingNext();
        return op.call(level, pos, state, flags);
    }
}

package nipah.edify.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.Level;
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EatBlockGoal.class)
public class EatBlockGoal_CauseMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private static boolean wrapFallSet(Level level, BlockPos pos, boolean dropBlock,
                                       Operation<Boolean> op) {
        Level_AnyBlockRemovedMixinRuntime.INSTANCE.preventPostingNext();
        return op.call(level, pos, dropBlock);
    }
}

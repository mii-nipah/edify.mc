package nipah.edify.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeavesBlock;
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LeavesBlock.class)
public class LeavesBlock_CauseMixin {
    @WrapOperation(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private static boolean wrapFallSet(ServerLevel level, BlockPos pos, boolean isMoving, Operation<Boolean> op) {
        Level_AnyBlockRemovedMixinRuntime.INSTANCE.preventPostingNext();
        return op.call(level, pos, isMoving);
    }
}

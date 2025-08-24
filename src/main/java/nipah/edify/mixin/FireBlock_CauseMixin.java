package nipah.edify.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireBlock.class)
public class FireBlock_CauseMixin {
    @WrapOperation(
            method = "checkBurnOut",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private static boolean wrapBurnOut(Level level, BlockPos pos, boolean isMoving,
                                       Operation<Boolean> op) {
        Level_AnyBlockRemovedMixinRuntime.INSTANCE.preventPostingNext();
        return op.call(level, pos, isMoving);
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private static boolean wrapTickRemove(ServerLevel level, BlockPos pos, boolean isMoving, Operation<Boolean> op) {
        Level_AnyBlockRemovedMixinRuntime.INSTANCE.preventPostingNext();
        return op.call(level, pos, isMoving);
    }
}

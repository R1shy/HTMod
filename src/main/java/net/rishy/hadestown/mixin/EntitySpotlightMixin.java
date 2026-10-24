package net.rishy.hadestown.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.rishy.hadestown.HadestownRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySpotlightMixin {
    @Inject(method = "move", at = @At("TAIL"))
    private void hadestown$refreshSpotlightAfterMovement(MoverType movementType, Vec3 movement, CallbackInfo callback) {
        hadestown$refreshSpotlight();
    }

    @Inject(method = "setPos(DDD)V", at = @At("TAIL"))
    private void hadestown$refreshSpotlightAfterPositionSet(double x, double y, double z, CallbackInfo callback) {
        hadestown$refreshSpotlight();
    }

    @Inject(method = "addTag", at = @At("TAIL"))
    private void hadestown$refreshSpotlightAfterTagAdded(
            String tag,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if ("lit".equals(tag)) {
            hadestown$refreshSpotlight();
        }
    }

    @Inject(method = "removeTag", at = @At("TAIL"))
    private void hadestown$refreshSpotlightAfterTagRemoved(
            String tag,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if ("lit".equals(tag)) {
            hadestown$refreshSpotlight();
        }
    }

    private void hadestown$refreshSpotlight() {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof ServerPlayer player) {
            HadestownRuntime.refreshSpotlight(player);
        }
    }
}

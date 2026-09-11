package net.rishy.hadestown.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SittingPassengerMixin {
    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void hadestown$allowPlayerPassengers(Entity passenger, CallbackInfoReturnable<Boolean> callbackInfo) {
        if ((Object) this instanceof Player && passenger instanceof Player) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "getPassengerRidingPosition", at = @At("RETURN"), cancellable = true)
    private void hadestown$stackPlayerPassengers(
            Entity passenger,
            CallbackInfoReturnable<Vec3> callbackInfo
    ) {
        if (!((Object) this instanceof Player vehicle) || !(passenger instanceof Player)) {
            return;
        }

        double verticalOffset = 0.0;
        for (Entity existingPassenger : vehicle.getPassengers()) {
            if (existingPassenger == passenger) {
                break;
            }
            verticalOffset += existingPassenger.getBbHeight();
        }

        if (verticalOffset != 0.0) {
            callbackInfo.setReturnValue(callbackInfo.getReturnValue().add(0.0, verticalOffset, 0.0));
        }
    }

    @Inject(method = "onPassengerTurned", at = @At("HEAD"), cancellable = true)
    private void hadestown$keepPlayerPassengerRotation(Entity passenger, CallbackInfo callbackInfo) {
        if ((Object) this instanceof Player && passenger instanceof Player) {
            callbackInfo.cancel();
        }
    }
}

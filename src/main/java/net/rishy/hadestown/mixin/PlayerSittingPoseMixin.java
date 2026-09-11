package net.rishy.hadestown.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.rishy.hadestown.SittingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerSittingPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void hadestown$applySittingPose(CallbackInfo callbackInfo) {
        Player player = (Player) (Object) this;
        Entity vehicle = player.getVehicle();
        if (player.isPassenger()
                && (vehicle instanceof Player || SittingManager.isSeatEntity(vehicle))) {
            player.setPose(Pose.SITTING);
        }
    }
}

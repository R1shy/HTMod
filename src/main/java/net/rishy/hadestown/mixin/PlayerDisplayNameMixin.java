package net.rishy.hadestown.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.rishy.hadestown.HadestownRoleHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void hadestown$applyRoleDisplayName(CallbackInfoReturnable<Component> callback) {
        Player player = (Player) (Object) this;
        if (player instanceof HadestownRoleHolder roleHolder && roleHolder.hadestown$getRole() != null) {
            callback.setReturnValue(roleHolder.hadestown$getRole().displayName(player.getGameProfile().name()));
        }
    }
}

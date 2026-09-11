package net.rishy.hadestown.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.rishy.hadestown.HadestownRole;
import net.rishy.hadestown.HadestownRoleHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDisplayNameMixin implements HadestownRoleHolder {
    @Unique
    private HadestownRole hadestown$role;

    @Override
    public HadestownRole hadestown$getRole() {
        return hadestown$role;
    }

    @Override
    public void hadestown$setRole(HadestownRole role) {
        hadestown$role = role;
    }

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void hadestown$applyTabListDisplayName(CallbackInfoReturnable<Component> callback) {
        if (hadestown$role != null) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            callback.setReturnValue(hadestown$role.displayName(player.getGameProfile().name()));
        }
    }
}

package net.rishy.hadestown.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.rishy.hadestown.HadestownRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerMovementMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void hadestown$refreshSpotlightAfterMovePacket(
            ServerboundMovePlayerPacket packet,
            CallbackInfo callback
    ) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        HadestownRuntime.refreshSpotlight(listener.getPlayer());
    }
}

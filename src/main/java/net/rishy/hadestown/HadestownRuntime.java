package net.rishy.hadestown;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HadestownRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hadestown");

    private final HadestownConfig config;
    private final HTSetHandler setHandler;
    private final SittingManager sittingManager;
    private ServerLevel world;

    public HadestownRuntime(HadestownConfig config) {
        this.config = config;
        this.setHandler = new HTSetHandler(config.sets());
        this.sittingManager = new SittingManager(config.sitting());
    }

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        UseBlockCallback.EVENT.register(this::onUseBlock);
        UseEntityCallback.EVENT.register(this::onUseEntity);
        ServerTickEvents.END_SERVER_TICK.register(sittingManager::tick);
        ServerPlayerEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> setRole(newPlayer));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            sittingManager.onPlayerJoin(newPlayer);
            updatePlayerDisplay(newPlayer);
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::allowChatMessage);
    }

    private void onServerStarted(MinecraftServer server) {
        Identifier worldIdentifier = Identifier.tryParse(config.worldId());
        if (worldIdentifier == null) {
            throw new IllegalStateException("Invalid configured world identifier: " + config.worldId());
        }

        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, worldIdentifier);
        world = server.getLevel(worldKey);
        if (world == null) {
            throw new IllegalStateException("Configured world is not loaded: " + config.worldId());
        }
        setHandler.reset();
        LOGGER.info("Hadestown controls initialized in {}", config.worldId());
    }

    private void registerCommands(
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandBuildContext buildContext,
            net.minecraft.commands.Commands.CommandSelection selection
    ) {
        dispatcher.register(Commands.literal("scene")
                .then(Commands.literal("set")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                         .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .executes(context -> {
                                            int x = IntegerArgumentType.getInteger(context, "x");
                                            int y = IntegerArgumentType.getInteger(context, "y");
                                            loadSet(context.getSource(), x, y);
                                             return 1;
                                         })))));

        dispatcher.register(Commands.literal("sit")
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        return 0;
                    }
                    return sittingManager.sitCommand(player);
                }));
    }

    private InteractionResult onUseBlock(
            Player player,
            Level level,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return sittingManager.useBlock(player, level, hand, hitResult);
        }
        if (!(level instanceof ServerLevel serverLevel) || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = hitResult.getBlockPos();
        if (serverLevel == world) {
            BlockState clickedState = serverLevel.getBlockState(clickedPos);
            if (clickedState.is(Blocks.NOTE_BLOCK)
                    && clickedPos.equals(config.reverseNoteBlockPos())) {
                setHandler.reverse();
                loadCurrentSet(player);
                return InteractionResult.PASS;
            }
            if (clickedState.is(Blocks.NOTE_BLOCK)
                    && clickedPos.equals(config.forwardNoteBlockPos())) {
                setHandler.advance();
                loadCurrentSet(player);
                return InteractionResult.PASS;
            }
        }
        return sittingManager.useBlock(player, serverLevel, hand, hitResult);
    }

    private InteractionResult onUseEntity(
            Player player,
            Level level,
            InteractionHand hand,
            Entity entity,
            EntityHitResult hitResult
    ) {
        return sittingManager.usePlayer(player, level, hand, entity);
    }

    private void loadSet(net.minecraft.commands.CommandSourceStack source, int x, int y) {
        if (!isReady(source)) {
            return;
        }

        BlockPos start;
        BlockPos end;
        try {
            start = setHandler.getSetStartPos(x, y);
            end = setHandler.getSetEndPos(x, y);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return;
        }

        if (!CMDUtils.cloneRegion(world, start, end, setHandler.getStageStartPos())) {
            source.sendFailure(Component.literal("Unable to load the requested set.").withStyle(ChatFormatting.RED));
            return;
        }

        // Keep command loads and button loads on the same navigation state.
        setHandler.setCurrentSet(x, y);
        updateInfoSign(source);
        source.sendSystemMessage(Component.literal(
                "Loaded set (" + x + ", " + y + ") | navigation count " + setHandler.getGlobalSetNumber()
        ));
    }

    private void loadCurrentSet(Player player) {
        if (!isReady(player)) {
            return;
        }

        if (!CMDUtils.cloneRegion(
                world,
                setHandler.getCurrentSetStartPos(),
                setHandler.getCurrentSetEndPos(),
                setHandler.getStageStartPos()
        )) {
            player.sendSystemMessage(Component.literal("Unable to load the current set.").withStyle(ChatFormatting.RED));
            return;
        }

        updateInfoSign(player);
        player.sendSystemMessage(Component.literal(
                "Scene " + setHandler.getSceneNumber() + " | Set " + setHandler.getGlobalSetNumber()
        ));
    }

    private boolean isReady(net.minecraft.commands.CommandSourceStack source) {
        if (world == null) {
            source.sendFailure(Component.literal("Hadestown is not ready yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private boolean isReady(Player player) {
        if (world == null) {
            player.sendSystemMessage(Component.literal("Hadestown is not ready yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private void updateInfoSign(net.minecraft.commands.CommandSourceStack source) {
        if (!updateInfoSign()) {
            source.sendSystemMessage(Component.literal("NOT SIGN").withStyle(ChatFormatting.RED));
        }
    }

    private void updateInfoSign(Player player) {
        if (!updateInfoSign()) {
            player.sendSystemMessage(Component.literal("NOT SIGN").withStyle(ChatFormatting.RED));
        }
    }

    private boolean updateInfoSign() {
        BlockPos signPos = config.infoSignPos();
        world.getChunkAt(signPos);
        if (!(world.getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
            return false;
        }

        SignText frontText = sign.getFrontText()
                .setHasGlowingText(true)
                .setMessage(1, Component.literal("CURRENT SET:"))
                .setMessage(2, Component.literal(String.valueOf(setHandler.getGlobalSetNumber())))
                .setMessage(3, Component.literal("SCENE " + setHandler.getSceneNumber()));
        sign.setText(frontText, true);
        sign.setChanged();

        BlockState state = world.getBlockState(signPos);
        world.sendBlockUpdated(signPos, state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private void onPlayerJoin(ServerPlayer player) {
        sittingManager.onPlayerJoin(player);
        updatePlayerDisplay(player);
    }

    private void setRole(ServerPlayer player) {
        if (player instanceof HadestownRoleHolder roleHolder) {
            roleHolder.hadestown$setRole(roleFor(player.getGameProfile().name()));
        }
    }

    private void updatePlayerDisplay(ServerPlayer player) {
        setRole(player);
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player)
            );
        }
    }

    private boolean allowChatMessage(
            PlayerChatMessage message,
            ServerPlayer sender,
            ChatType.Bound boundChatType
    ) {
        HadestownRole role = roleFor(sender.getGameProfile().name());
        if (role == null) {
            return true;
        }

        Component formatted = role.chatMessage(sender.getGameProfile().name(), message.decoratedContent());
        sender.level().getServer().getPlayerList().broadcastSystemMessage(formatted, false);
        return false;
    }

    private HadestownRole roleFor(String name) {
        if (name.equalsIgnoreCase(config.developer())) {
            return HadestownRole.DEVELOPER;
        }
        if (config.directors().contains(name)) {
            return HadestownRole.DIRECTOR;
        }
        if (config.crew().contains(name)) {
            return HadestownRole.CREW;
        }
        return null;
    }
}

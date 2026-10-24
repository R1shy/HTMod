package net.rishy.hadestown;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HadestownRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hadestown");
    private static volatile HadestownRuntime instance;
    private static final BlockState SPOTLIGHT_STATE = Blocks.LIGHT.defaultBlockState()
            .setValue(LightBlock.LEVEL, 10);

    private final HadestownConfig config;
    private final HTSetHandler setHandler;
    private final Map<UUID, SpotlightLocation> playerSpotlights = new HashMap<>();
    private final Map<SpotlightLocation, Integer> spotlightPlayerCounts = new HashMap<>();
    private final Map<SpotlightLocation, BlockState> placedSpotlights = new HashMap<>();
    private final Set<UUID> onlinePlayers = new HashSet<>();
    private ServerLevel world;

    public HadestownRuntime(HadestownConfig config) {
        this.config = config;
        this.setHandler = new HTSetHandler(config.sets());
        instance = this;
    }

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(this::tickSpotlights);
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        UseBlockCallback.EVENT.register(this::onUseBlock);
        ServerPlayerEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> setRole(newPlayer));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            updatePlayerDisplay(newPlayer);
            updateSpotlight(newPlayer);
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::allowChatMessage);
    }

    public static void refreshSpotlight(ServerPlayer player) {
        HadestownRuntime runtime = instance;
        if (runtime != null) {
            runtime.updateSpotlight(player);
        }
    }

    private void tickSpotlights(MinecraftServer server) {
        onlinePlayers.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayers.add(player.getUUID());
            updateSpotlight(player);
        }

        Iterator<Map.Entry<UUID, SpotlightLocation>> iterator = playerSpotlights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpotlightLocation> playerSpotlight = iterator.next();
            if (!onlinePlayers.contains(playerSpotlight.getKey())) {
                iterator.remove();
                releaseSpotlight(playerSpotlight.getValue());
            }
        }
    }

    private void updateSpotlight(ServerPlayer player) {
        UUID playerId = player.getUUID();
        SpotlightLocation previousLocation = playerSpotlights.get(playerId);
        SpotlightLocation currentLocation = null;
        if (player.entityTags().contains("lit")) {
            ServerLevel level = player.level();
            BlockPos position = player.blockPosition().above(3);
            if (level.isInWorldBounds(position)) {
                currentLocation = new SpotlightLocation(level, position);
            }
        }

        if (currentLocation != null && currentLocation.equals(previousLocation)) {
            placeSpotlight(currentLocation);
            return;
        }

        if (previousLocation != null) {
            playerSpotlights.remove(playerId);
            releaseSpotlight(previousLocation);
        }
        if (currentLocation != null) {
            playerSpotlights.put(playerId, currentLocation);
            int playerCount = spotlightPlayerCounts.getOrDefault(currentLocation, 0);
            spotlightPlayerCounts.put(currentLocation, playerCount + 1);
            placeSpotlight(currentLocation);
        }
    }

    private void placeSpotlight(SpotlightLocation location) {
        BlockState currentState = location.level().getBlockState(location.position());
        if (currentState.equals(SPOTLIGHT_STATE)) {
            return;
        }
        // Preserve existing blocks rather than replacing a ceiling or block entity.
        if (!currentState.isAir() && !currentState.is(Blocks.LIGHT)) {
            return;
        }

        BlockState previousState = placedSpotlights.get(location);
        if (previousState == null) {
            previousState = currentState;
        }
        if (location.level().setBlockAndUpdate(location.position(), SPOTLIGHT_STATE)) {
            placedSpotlights.put(location, previousState);
        }
    }

    private void releaseSpotlight(SpotlightLocation location) {
        Integer playerCount = spotlightPlayerCounts.get(location);
        if (playerCount == null) {
            return;
        }
        if (playerCount > 1) {
            spotlightPlayerCounts.put(location, playerCount - 1);
            return;
        }

        spotlightPlayerCounts.remove(location);
        BlockState previousState = placedSpotlights.remove(location);
        if (previousState == null) {
            return;
        }

        // Load the previous position so teleports do not leave a stale spotlight behind.
        location.level().getChunkAt(location.position());
        if (location.level().getBlockState(location.position()).equals(SPOTLIGHT_STATE)) {
            location.level().setBlockAndUpdate(location.position(), previousState);
        }
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
    }

    private InteractionResult onUseBlock(
            Player player,
            Level level,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
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
        return InteractionResult.PASS;
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
        updatePlayerDisplay(player);
        updateSpotlight(player);
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
        if (config.cast().contains(name)) {
            return HadestownRole.CAST;
        }
        if (config.crew().contains(name)) {
            return HadestownRole.CREW;
        }
        return null;
    }

    private record SpotlightLocation(ServerLevel level, BlockPos position) {
    }
}

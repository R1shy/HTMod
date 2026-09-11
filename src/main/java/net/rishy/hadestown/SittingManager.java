package net.rishy.hadestown;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SittingManager {
    public static final String SEAT_NAME = "hadestown-sitting-seat";

    private static final Identifier TEXT_DISPLAY_ID = Identifier.fromNamespaceAndPath("minecraft", "text_display");
    private static final int VALIDATION_INTERVAL = 5;

    private final SittingSettings settings;
    private final Map<UUID, Display.TextDisplay> seats = new HashMap<>();
    private final Map<UUID, Display.TextDisplay> pendingSeats = new HashMap<>();
    private int validationTicks;

    public SittingManager(SittingSettings settings) {
        this.settings = settings;
    }

    public InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (!settings.enabled()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return canSit(player, level, hand, hitResult, false)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!canSit(serverPlayer, serverLevel, hand, hitResult, true)) {
            return InteractionResult.PASS;
        }

        UUID playerId = serverPlayer.getUUID();
        if (seats.containsKey(playerId) || pendingSeats.containsKey(playerId)) {
            return InteractionResult.PASS;
        }

        BlockPos blockPos = hitResult.getBlockPos();
        if (findSeat(serverLevel, blockPos, null) != null) {
            return InteractionResult.PASS;
        }

        double height = getSittingHeight(serverLevel, blockPos, true);
        if (height < 0.0) {
            return InteractionResult.PASS;
        }

        pendingSeats.put(playerId, createSeat(serverLevel, blockPos, height));
        return InteractionResult.SUCCESS;
    }

    public InteractionResult usePlayer(Player player, Level level, InteractionHand hand, Entity entity) {
        if (!settings.enabled()
                || !(entity instanceof Player target)
                || target == player
                || !target.isAlive()
                || target.isSpectator()
                || hand != InteractionHand.MAIN_HAND
                || !canSitWhileActive(player)
                || !handsAllowed(player)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(target.level() instanceof ServerLevel)) {
            return InteractionResult.PASS;
        }

        if (!serverPlayer.startRiding(target)) {
            return InteractionResult.PASS;
        }
        serverPlayer.setPose(Pose.SITTING);
        target.positionRider(serverPlayer);
        return InteractionResult.SUCCESS;
    }

    public int sitCommand(ServerPlayer player) {
        if (!settings.enabled() || !canSitWhileActive(player)) {
            return 0;
        }

        double reach = player.blockInteractionRange();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(reach));
        ServerLevel level = (ServerLevel) player.level();
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.BLOCK && requestSeat(player, hit.getBlockPos(), null)) {
            return 1;
        }

        BlockPos fallback = player.blockPosition();
        if (player.getY() - player.getBlockY() <= 0.0) {
            fallback = fallback.below();
        }
        return requestSeat(player, fallback, null) ? 1 : 0;
    }

    public void onPlayerJoin(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (isSeatEntity(vehicle)) {
            seats.put(player.getUUID(), (Display.TextDisplay) vehicle);
        }
    }

    public void tick(MinecraftServer server) {
        spawnPending(server);

        validationTicks++;
        if (validationTicks < VALIDATION_INTERVAL) {
            return;
        }
        validationTicks = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isSeatEntity(player.getVehicle())) {
                seats.put(player.getUUID(), (Display.TextDisplay) player.getVehicle());
            }
        }

        for (Map.Entry<UUID, Display.TextDisplay> entry : new ArrayList<>(seats.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Display.TextDisplay seat = entry.getValue();

            if (player == null) {
                if (!settings.keepActive()) {
                    removeSeat(entry.getKey(), seat);
                }
                continue;
            }

            if (player.getVehicle() != seat || !isValidSeat(player, seat)) {
                removeSeat(entry.getKey(), seat);
            }
        }
    }

    public static boolean isSeatEntity(Entity entity) {
        return entity instanceof Display.TextDisplay textDisplay
                && textDisplay.hasCustomName()
                && textDisplay.getCustomName().getString().equals(SEAT_NAME);
    }

    private void spawnPending(MinecraftServer server) {
        for (Map.Entry<UUID, Display.TextDisplay> entry : new ArrayList<>(pendingSeats.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Display.TextDisplay seat = entry.getValue();
            pendingSeats.remove(entry.getKey());

            if (player == null || !canSitWhileActive(player)) {
                seat.discard();
                continue;
            }

            if (!(seat.level() instanceof ServerLevel serverLevel)
                    || !serverLevel.addFreshEntity(seat)
                    || !player.startRiding(seat)) {
                seat.discard();
                continue;
            }

            player.setPose(Pose.SITTING);
            seat.positionRider(player);
            seats.put(player.getUUID(), seat);
        }
    }

    private boolean requestSeat(ServerPlayer player, BlockPos blockPos, BlockHitResult hitResult) {
        if (!canSitWhileActive(player)
                || hitResult != null && !handsAllowed(player)
                || !canSitAt(player, blockPos, hitResult != null)) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (seats.containsKey(player.getUUID()) || pendingSeats.containsKey(player.getUUID())) {
            return false;
        }
        if (findSeat(level, blockPos, null) != null) {
            return false;
        }

        double height = getSittingHeight(level, blockPos, hitResult != null);
        if (height < 0.0) {
            return false;
        }
        pendingSeats.put(player.getUUID(), createSeat(level, blockPos, height));
        return true;
    }

    private boolean canSit(
            Player player,
            Level level,
            InteractionHand hand,
            BlockHitResult hitResult,
            boolean serverCheck
    ) {
        if (!canSitWhileActive(player) || !handsAllowed(player)) {
            return false;
        }
        if (hitResult != null && hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        BlockPos blockPos = hitResult == null ? player.blockPosition() : hitResult.getBlockPos();
        if (!canSitAt(player, blockPos, hitResult != null)) {
            return false;
        }
        if (serverCheck && level instanceof ServerLevel serverLevel) {
            return findSeat(serverLevel, blockPos, null) == null;
        }
        return true;
    }

    private boolean canSitAt(Player player, BlockPos blockPos, boolean handInteraction) {
        int playerY = player.getBlockY();
        int blockY = blockPos.getY();
        double limit = playerY < blockY ? settings.yLimitAbove() : settings.yLimitBelow();
        if (Math.abs(playerY - blockY) > limit) {
            return false;
        }

        Level level = player.level();
        double height = getSittingHeight(level, blockPos, handInteraction);
        if (height < 0.0) {
            return false;
        }

        BlockPos above = blockPos.above();
        BlockPos secondAbove = height > 0.8 ? blockPos.above(2) : above;
        BlockPos below = height > 0.8 ? blockPos : blockPos.below();
        if (!isClear(level, above) || !isClear(level, secondAbove)) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            return findSeat(serverLevel, blockPos, null) == null
                    && findSeat(serverLevel, above, null) == null
                    && findSeat(serverLevel, secondAbove, null) == null
                    && findSeat(serverLevel, below, null) == null;
        }
        return true;
    }

    private boolean isClear(Level level, BlockPos blockPos) {
        BlockState state = level.getBlockState(blockPos);
        if (settings.allowedAboveSeat().stream().anyMatch(rule -> rule.matches(state))) {
            return true;
        }
        return state.getCollisionShape(level, blockPos).isEmpty();
    }

    private double getSittingHeight(Level level, BlockPos blockPos, boolean handInteraction) {
        BlockState state = level.getBlockState(blockPos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return -1.0;
        }
        if (settings.blacklistedBlocks().stream().anyMatch(rule -> rule.matches(state))) {
            return -1.0;
        }
        if (handInteraction
                && settings.interactionBlocks().stream().anyMatch(rule -> rule.matches(state))) {
            return -1.0;
        }
        if (settings.customEnabled()) {
            for (SittingBlockRule rule : settings.customBlocks()) {
                if (rule.matches(state)) {
                    return rule.sittingHeight();
                }
            }
        }
        if (settings.stairs()
                && state.getBlock() instanceof StairBlock
                && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
            return 0.5;
        }
        if (settings.slabs()
                && state.getBlock() instanceof SlabBlock
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return 0.5;
        }
        if (settings.carpets() && state.getBlock() instanceof CarpetBlock) {
            return 0.062;
        }
        if (settings.fullBlocks() && state.isCollisionShapeFullBlock(level, blockPos)) {
            return 1.0;
        }
        return -1.0;
    }

    private Display.TextDisplay createSeat(ServerLevel level, BlockPos blockPos, double height) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(TEXT_DISPLAY_ID);
        Display.TextDisplay seat = new Display.TextDisplay(type, level);
        seat.setText(Component.empty());
        seat.setCustomName(Component.literal(SEAT_NAME));
        seat.setCustomNameVisible(false);
        seat.setInvisible(true);
        seat.setInvulnerable(true);
        seat.setPos(blockPos.getX() + 0.5, blockPos.getY() + height, blockPos.getZ() + 0.5);
        seat.setXRot(height >= 1.0 ? 90.0F : -90.0F);
        return seat;
    }

    private Display.TextDisplay findSeat(ServerLevel level, BlockPos blockPos, Display.TextDisplay ignored) {
        AABB area = new AABB(
                blockPos.getX(),
                blockPos.getY() - 1.0,
                blockPos.getZ(),
                blockPos.getX() + 1.0,
                blockPos.getY() + 2.0,
                blockPos.getZ() + 1.0
        );
        return level.getEntities(
                        EntityTypeTest.forClass(Display.TextDisplay.class),
                        area,
                        entity -> entity != ignored
                                && isSeatEntity(entity)
                                && seatBlockPos(entity).equals(blockPos)
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private boolean isValidSeat(ServerPlayer player, Display.TextDisplay seat) {
        if (!seat.isAlive() || !isSeatEntity(seat)) {
            return false;
        }
        BlockPos blockPos = seatBlockPos(seat);
        ServerLevel level = (ServerLevel) player.level();
        double height = getSittingHeight(level, blockPos, false);
        if (height < 0.0) {
            return false;
        }

        BlockPos above = blockPos.above();
        BlockPos secondAbove = height > 0.8 ? blockPos.above(2) : above;
        BlockPos below = height > 0.8 ? blockPos : blockPos.below();
        return isClearExceptSeat(level, above, seat)
                && isClearExceptSeat(level, secondAbove, seat)
                && findSeat(level, blockPos, seat) == null
                && findSeat(level, below, seat) == null;
    }

    private boolean isClearExceptSeat(ServerLevel level, BlockPos blockPos, Display.TextDisplay seat) {
        BlockState state = level.getBlockState(blockPos);
        if (!state.getCollisionShape(level, blockPos).isEmpty()
                && settings.allowedAboveSeat().stream().noneMatch(rule -> rule.matches(state))) {
            return false;
        }
        return findSeat(level, blockPos, seat) == null;
    }

    private void removeSeat(UUID playerId, Display.TextDisplay seat) {
        seats.remove(playerId);
        pendingSeats.remove(playerId);
        if (seat != null && seat.isAlive()) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    private boolean canSitWhileActive(Player player) {
        return !player.isSpectator()
                && !player.isShiftKeyDown()
                && (settings.sitWhileSeated() || !player.isPassenger());
    }

    private boolean handsAllowed(Player player) {
        if (!settings.handSitting()) {
            return true;
        }
        if (settings.mainHandEmpty() && !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return false;
        }

        var offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHand.isEmpty() || settings.offHandEmpty()) {
            return settings.offHandEmpty() ? offHand.isEmpty() : true;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
        return settings.offHandAllowedItems().stream().anyMatch(itemId::equalsIgnoreCase)
                || settings.offHandFood()
                && (offHand.getUseAnimation() == ItemUseAnimation.EAT
                || offHand.getUseAnimation() == ItemUseAnimation.DRINK);
    }

    public static BlockPos seatBlockPos(Display.TextDisplay seat) {
        int y = Mth.floor(seat.getY());
        if (seat.getXRot() > 0.0F) {
            y--;
        }
        return new BlockPos(Mth.floor(seat.getX()), y, Mth.floor(seat.getZ()));
    }
}

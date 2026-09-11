package net.rishy.hadestown.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.rishy.hadestown.SittingManager;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(Display.TextDisplay.class)
public abstract class SittingSeatDismountMixin extends Display {
    protected SittingSeatDismountMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (!SittingManager.isSeatEntity(this)) {
            return super.getDismountLocationForPassenger(passenger);
        }

        BlockPos anchor = SittingManager.seatBlockPos((Display.TextDisplay) (Object) this);
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(anchor);

        Direction left = passenger.getDirection().getCounterClockWise();
        candidates.add(anchor.relative(left));
        candidates.add(anchor.relative(left.getOpposite()));
        candidates.add(anchor.relative(passenger.getDirection()));
        candidates.add(anchor.relative(passenger.getDirection().getOpposite()));
        candidates.add(anchor.above());

        for (Pose pose : passenger.getDismountPoses()) {
            for (BlockPos candidate : candidates) {
                Vec3 position = dismountPosition(candidate);
                AABB bounds = passenger.getDimensions(pose).makeBoundingBox(position);
                if (level().noCollision(passenger, bounds)) {
                    passenger.setPose(pose);
                    return position;
                }
            }
        }

        return super.getDismountLocationForPassenger(passenger);
    }

    private Vec3 dismountPosition(BlockPos blockPos) {
        double height = level().getBlockState(blockPos)
                .getCollisionShape(level(), blockPos)
                .max(Direction.Axis.Y);
        return new Vec3(blockPos.getX() + 0.5, blockPos.getY() + height, blockPos.getZ() + 0.5);
    }
}

package net.rishy.hadestown;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CMDUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hadestown");

    private CMDUtils() {
    }

    public static boolean cloneRegion(ServerLevel world, BlockPos startPos, BlockPos endPos, BlockPos destination) {
        loadRegion(world, startPos, endPos);

        com.sk89q.worldedit.world.World worldEditWorld = FabricAdapter.get().fromNativeWorld(world);
        CuboidRegion region = new CuboidRegion(
                worldEditWorld,
                toVector(startPos),
                toVector(endPos)
        );

        BlockVector3 origin = toVector(startPos);
        BlockVector3 to = toVector(destination);
        BlockVector3 destinationMin = to.add(region.getMinimumPoint().subtract(origin));
        BlockVector3 destinationMax = to.add(region.getMaximumPoint().subtract(origin));
        loadRegion(world, fromVector(destinationMin), fromVector(destinationMax));

        LOGGER.info(
                "Cloning region {} -> {} ({} blocks) into {} -> {}",
                region.getMinimumPoint(),
                region.getMaximumPoint(),
                region.getVolume(),
                destinationMin,
                destinationMax
        );

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(origin);

        try (EditSession source = WorldEdit.getInstance().newEditSessionBuilder()
                .world(worldEditWorld)
                .maxBlocks(-1)
                .build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(source, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);
            Operations.complete(copy);
        } catch (Exception e) {
            LOGGER.error("Scene copy failed", e);
            return false;
        }

        try (EditSession destinationEdit = WorldEdit.getInstance().newEditSessionBuilder()
                .world(worldEditWorld)
                .maxBlocks(-1)
                .build()) {
            Operation paste = new ClipboardHolder(clipboard)
                    .createPaste(destinationEdit)
                    .to(to)
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(paste);
            LOGGER.info("Scene paste completed");
            return true;
        } catch (Exception e) {
            LOGGER.error("Scene paste failed", e);
            return false;
        }
    }

    private static BlockVector3 toVector(BlockPos pos) {
        return BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos fromVector(BlockVector3 pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static void loadRegion(ServerLevel world, BlockPos first, BlockPos second) {
        int minChunkX = Math.min(first.getX(), second.getX()) >> 4;
        int maxChunkX = Math.max(first.getX(), second.getX()) >> 4;
        int minChunkZ = Math.min(first.getZ(), second.getZ()) >> 4;
        int maxChunkZ = Math.max(first.getZ(), second.getZ()) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }
    }
}

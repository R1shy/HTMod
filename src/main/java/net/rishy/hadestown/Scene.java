package net.rishy.hadestown;

import net.minecraft.core.BlockPos;

import java.util.Locale;

public final class Scene {
    private final BlockPos firstSetPos;
    private final int setDistance;
    private final int setWidth;
    private final int setLength;
    private final int setHeight;
    private final int numSets;
    private final String setDirection;
    private final String sceneDirection;

    public Scene(
            BlockPos firstSetPos,
            int setDistance,
            int setWidth,
            int setLength,
            int setHeight,
            int numSets,
            String setDirection,
            String sceneDirection
    ) {
        this.firstSetPos = firstSetPos;
        this.setDistance = setDistance;
        this.setWidth = setWidth;
        this.setLength = setLength;
        this.setHeight = setHeight;
        this.numSets = numSets;
        this.setDirection = setDirection;
        this.sceneDirection = sceneDirection;
    }

    public static void validateDirection(String direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Invalid direction: null");
        }
        switch (direction.toLowerCase(Locale.ROOT)) {
            case "x", "+x", "-x", "z", "+z", "-z", "y", "+y", "-y" -> {
            }
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        }
    }

    public int getNumSets() {
        return numSets;
    }

    public BlockPos getSetStartPos(int setNumber) {
        if (setNumber < 1 || setNumber > numSets) {
            throw new IllegalArgumentException("Set number must be between 1 and " + numSets + ".");
        }
        int setStride = setWidth + setDistance;
        return move(firstSetPos, setStride * (setNumber - 1), setDirection);
    }

    public BlockPos getSetEndPos(int setNumber) {
        BlockPos end = getSetStartPos(setNumber);
        end = move(end, setLength - 1, sceneDirection);
        end = move(end, setHeight - 1, "y");
        return move(end, setWidth - 1, setDirection);
    }

    static BlockPos move(BlockPos pos, int distance, String direction) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "x", "+x" -> pos.offset(distance, 0, 0);
            case "-x" -> pos.offset(-distance, 0, 0);
            case "z", "+z" -> pos.offset(0, 0, distance);
            case "-z" -> pos.offset(0, 0, -distance);
            case "y", "+y" -> pos.offset(0, distance, 0);
            case "-y" -> pos.offset(0, -distance, 0);
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        };
    }
}

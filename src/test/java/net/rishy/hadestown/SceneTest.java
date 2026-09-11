package net.rishy.hadestown;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneTest {
    @Test
    void calculatesSetBoundsUsingConfiguredDirections() {
        Scene scene = new Scene(
                new BlockPos(10, 20, 30),
                2,
                3,
                4,
                5,
                2,
                "-x",
                "z"
        );

        assertEquals(new BlockPos(5, 20, 30), scene.getSetStartPos(2));
        assertEquals(new BlockPos(3, 24, 33), scene.getSetEndPos(2));
    }

    @Test
    void rejectsInvalidSetNumbersAndDirections() {
        Scene scene = new Scene(BlockPos.ZERO, 0, 3, 3, 3, 1, "x", "z");

        assertThrows(IllegalArgumentException.class, () -> scene.getSetStartPos(0));
        assertThrows(IllegalArgumentException.class, () -> Scene.validateDirection("north"));
    }
}

package net.rishy.hadestown;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HTSetHandlerTest {
    @Test
    void advancesAcrossScenesAndWraps() {
        HTSetHandler handler = handler(List.of(2, 1, 3));

        handler.advance();
        assertState(handler, 1, 1, 1);
        handler.advance();
        assertState(handler, 1, 2, 2);
        handler.advance();
        assertState(handler, 2, 1, 3);
        handler.setCurrentSet(2, 2);
        assertState(handler, 3, 3, 6);
        handler.advance();
        assertState(handler, 1, 1, 1);
    }

    @Test
    void reversesFromFirstSetToLastSet() {
        HTSetHandler handler = handler(List.of(2, 1, 3));

        handler.reverse();
        assertState(handler, 3, 3, 6);
        handler.reverse();
        assertState(handler, 3, 2, 5);
        handler.setCurrentSet(0, 0);
        handler.reverse();
        assertState(handler, 3, 3, 6);
    }

    @Test
    void validatesZeroBasedCoordinates() {
        HTSetHandler handler = handler(List.of(2, 1, 3));

        assertThrows(IllegalArgumentException.class, () -> handler.getSetStartPos(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> handler.getSetStartPos(2, 0));
        assertThrows(IllegalArgumentException.class, () -> handler.getSetStartPos(0, 3));
    }

    private static HTSetHandler handler(List<Integer> setsPerScene) {
        return new HTSetHandler(new HadestownConfig.SetSettings(
                BlockPos.ZERO,
                new BlockPos(10, 20, 30),
                2,
                4,
                3,
                4,
                5,
                "-x",
                "z",
                setsPerScene
        ));
    }

    private static void assertState(HTSetHandler handler, int scene, int localSet, int globalSet) {
        assertEquals(scene, handler.getSceneNumber());
        assertEquals(localSet, handler.getLocalSetNumber());
        assertEquals(globalSet, handler.getGlobalSetNumber());
    }
}

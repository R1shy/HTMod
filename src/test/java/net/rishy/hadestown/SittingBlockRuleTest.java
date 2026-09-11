package net.rishy.hadestown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SittingBlockRuleTest {
    @Test
    void clampsConfiguredSittingHeight() {
        SittingBlockRule rule = new SittingBlockRule(
                List.of("minecraft:oak_log"),
                List.of(),
                List.of("!axis=y"),
                2.0
        );

        assertEquals(1.0, rule.sittingHeight());
    }

    @Test
    void defaultsKeepLinkedPresetBehavior() {
        SittingSettings settings = SittingSettings.defaults();

        assertTrue(settings.enabled());
        assertTrue(settings.stairs());
        assertTrue(settings.slabs());
        assertTrue(settings.carpets());
        assertFalse(settings.fullBlocks());
        assertEquals(1.0, settings.yLimitAbove());
        assertEquals(1.0, settings.yLimitBelow());
    }
}

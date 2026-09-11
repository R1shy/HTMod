package net.rishy.hadestown;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class SittingSettings {
    private final boolean enabled;
    private final boolean keepActive;
    private final boolean sitWhileSeated;
    private final boolean handSitting;
    private final boolean mainHandEmpty;
    private final boolean offHandEmpty;
    private final boolean offHandFood;
    private final List<String> offHandAllowedItems;
    private final boolean stairs;
    private final boolean slabs;
    private final boolean carpets;
    private final boolean fullBlocks;
    private final boolean customEnabled;
    private final double yLimitAbove;
    private final double yLimitBelow;
    private final List<SittingBlockRule> customBlocks;
    private final List<SittingBlockRule> blacklistedBlocks;
    private final List<SittingBlockRule> interactionBlocks;
    private final List<SittingBlockRule> allowedAboveSeat;

    private SittingSettings(
            boolean enabled,
            boolean keepActive,
            boolean sitWhileSeated,
            boolean handSitting,
            boolean mainHandEmpty,
            boolean offHandEmpty,
            boolean offHandFood,
            List<String> offHandAllowedItems,
            boolean stairs,
            boolean slabs,
            boolean carpets,
            boolean fullBlocks,
            boolean customEnabled,
            double yLimitAbove,
            double yLimitBelow,
            List<SittingBlockRule> customBlocks,
            List<SittingBlockRule> blacklistedBlocks,
            List<SittingBlockRule> interactionBlocks,
            List<SittingBlockRule> allowedAboveSeat
    ) {
        this.enabled = enabled;
        this.keepActive = keepActive;
        this.sitWhileSeated = sitWhileSeated;
        this.handSitting = handSitting;
        this.mainHandEmpty = mainHandEmpty;
        this.offHandEmpty = offHandEmpty;
        this.offHandFood = offHandFood;
        this.offHandAllowedItems = List.copyOf(offHandAllowedItems);
        this.stairs = stairs;
        this.slabs = slabs;
        this.carpets = carpets;
        this.fullBlocks = fullBlocks;
        this.customEnabled = customEnabled;
        this.yLimitAbove = yLimitAbove;
        this.yLimitBelow = yLimitBelow;
        this.customBlocks = List.copyOf(customBlocks);
        this.blacklistedBlocks = List.copyOf(blacklistedBlocks);
        this.interactionBlocks = List.copyOf(interactionBlocks);
        this.allowedAboveSeat = List.copyOf(allowedAboveSeat);
    }

    public static SittingSettings fromJson(JsonElement element) {
        if (element == null) {
            return defaults();
        }
        if (!element.isJsonObject()) {
            throw invalid("sitting must be an object");
        }

        JsonObject object = element.getAsJsonObject();
        JsonObject presets = object(object, "preset-blocks");
        JsonObject limits = object(object, "height-difference-limit");
        JsonObject hands = object(object, "hands");

        return new SittingSettings(
                bool(object, "enabled", true),
                bool(object, "keep-active", true),
                bool(object, "sit-while-seated", false),
                bool(hands, "enabled", true),
                bool(hands, "main-hand-empty", true),
                bool(hands, "off-hand-empty", false),
                bool(hands, "off-hand-food", true),
                strings(hands, "off-hand-allowed-items", defaultOffHandItems()),
                bool(presets, "stairs", true),
                bool(presets, "slabs", true),
                bool(presets, "carpets", true),
                bool(presets, "full-blocks", false),
                bool(object, "custom-enabled", false),
                number(limits, "above", 1.0),
                number(limits, "below", 1.0),
                rules(object, "custom-blocks", 0.5),
                rules(object, "blacklisted-blocks", 0.0),
                rules(object, "interaction-blocks", 0.0),
                rules(object, "allowed-above-seat", 0.0)
        );
    }

    public static SittingSettings defaults() {
        return new SittingSettings(
                true,
                true,
                false,
                true,
                true,
                false,
                true,
                defaultOffHandItems(),
                true,
                true,
                true,
                false,
                false,
                1.0,
                1.0,
                List.of(
                        rule(List.of(), List.of("#minecraft:campfires"), List.of("lit=false"), 0.437),
                        rule(
                                List.of("!minecraft:crimson_stem", "!minecraft:warped_stem", "minecraft:polished_basalt"),
                                List.of("#minecraft:logs", "!#minecraft:oak_logs"),
                                List.of("!axis=y"),
                                1.0
                        ),
                        rule(List.of(), List.of("#minecraft:beds"), List.of("part=foot", "occupied=false"), 0.5625)
                ),
                List.of(rule(List.of(), List.of("#minecraft:shulker_boxes"), List.of(), 0.0)),
                List.of(rule(
                        List.of(
                                "minecraft:crafter",
                                "minecraft:repeating_command_block",
                                "minecraft:chain_command_block",
                                "minecraft:command_block",
                                "minecraft:trapdoor"
                        ),
                        List.of(
                                "#minecraft:shulker_boxes",
                                "#c:player_workstations/furnaces",
                                "#c:player_workstations/crafting_tables",
                                "#c:villager_job_sites",
                                "#c:chests"
                        ),
                        List.of(),
                        0.0
                )),
                List.of(
                        rule(List.of(), List.of("#minecraft:trapdoors"), List.of("open=true"), 0.0),
                        rule(List.of(), List.of("#minecraft:doors"), List.of(), 0.0)
                )
        );
    }

    private static SittingBlockRule rule(
            List<String> blockIds,
            List<String> blockTags,
            List<String> blockStates,
            double height
    ) {
        return new SittingBlockRule(blockIds, blockTags, blockStates, height);
    }

    private static List<SittingBlockRule> rules(JsonObject object, String key, double defaultHeight) {
        JsonElement element = object.get(key);
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw invalid(key + " must be an array");
        }

        List<SittingBlockRule> rules = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement rule : array) {
            rules.add(SittingBlockRule.fromJson(rule, defaultHeight));
        }
        return rules;
    }

    private static List<String> defaultOffHandItems() {
        return List.of(
                "minecraft:filled_map",
                "minecraft:torch",
                "minecraft:soul_torch",
                "minecraft:redstone_torch",
                "minecraft:lantern",
                "minecraft:soul_lantern"
        );
    }

    private static List<String> strings(JsonObject object, String key, List<String> defaultValue) {
        JsonElement element = object.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (!element.isJsonArray()) {
            throw invalid(key + " must be an array of strings");
        }

        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalid(key + " must be an array of strings");
            }
            values.add(value.getAsString());
        }
        return values;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null) {
            return new JsonObject();
        }
        if (!element.isJsonObject()) {
            throw invalid(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static boolean bool(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw invalid(key + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static double number(JsonObject object, String key, double defaultValue) {
        JsonElement element = object.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid(key + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value < 0.0) {
            throw invalid(key + " must be a finite non-negative number");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid sitting configuration: " + message);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean keepActive() {
        return keepActive;
    }

    public boolean sitWhileSeated() {
        return sitWhileSeated;
    }

    public boolean handSitting() {
        return handSitting;
    }

    public boolean mainHandEmpty() {
        return mainHandEmpty;
    }

    public boolean offHandEmpty() {
        return offHandEmpty;
    }

    public boolean offHandFood() {
        return offHandFood;
    }

    public List<String> offHandAllowedItems() {
        return offHandAllowedItems;
    }

    public boolean stairs() {
        return stairs;
    }

    public boolean slabs() {
        return slabs;
    }

    public boolean carpets() {
        return carpets;
    }

    public boolean fullBlocks() {
        return fullBlocks;
    }

    public boolean customEnabled() {
        return customEnabled;
    }

    public double yLimitAbove() {
        return yLimitAbove;
    }

    public double yLimitBelow() {
        return yLimitBelow;
    }

    public List<SittingBlockRule> customBlocks() {
        return customBlocks;
    }

    public List<SittingBlockRule> blacklistedBlocks() {
        return blacklistedBlocks;
    }

    public List<SittingBlockRule> interactionBlocks() {
        return interactionBlocks;
    }

    public List<SittingBlockRule> allowedAboveSeat() {
        return allowedAboveSeat;
    }
}

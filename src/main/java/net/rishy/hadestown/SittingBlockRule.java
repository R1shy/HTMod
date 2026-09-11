package net.rishy.hadestown;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class SittingBlockRule {
    private final List<String> blockIds;
    private final List<String> blockTags;
    private final List<String> blockStates;
    private final double sittingHeight;

    public SittingBlockRule(
            List<String> blockIds,
            List<String> blockTags,
            List<String> blockStates,
            double sittingHeight
    ) {
        this.blockIds = List.copyOf(blockIds);
        this.blockTags = List.copyOf(blockTags);
        this.blockStates = List.copyOf(blockStates);
        this.sittingHeight = Math.clamp(sittingHeight, 0.0, 1.0);
    }

    public static SittingBlockRule fromJson(JsonElement element, double defaultHeight) {
        if (element == null || !element.isJsonObject()) {
            throw invalid("block rules must be objects");
        }

        JsonObject object = element.getAsJsonObject();
        return new SittingBlockRule(
                strings(object, "block-ids"),
                strings(object, "block-tags"),
                strings(object, "blockstates"),
                number(object, "sitting-height", defaultHeight)
        );
    }

    public boolean matches(BlockState state) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        boolean hasPositiveType = false;
        boolean matchesType = false;

        for (String configuredId : blockIds) {
            boolean excluded = configuredId.startsWith("!");
            String id = excluded ? configuredId.substring(1) : configuredId;
            if (!excluded) {
                hasPositiveType = true;
            }
            if (id.equalsIgnoreCase(blockId.toString())) {
                if (excluded) {
                    return false;
                }
                matchesType = true;
            }
        }

        for (String configuredTag : blockTags) {
            boolean excluded = configuredTag.startsWith("!");
            String tagId = excluded ? configuredTag.substring(1) : configuredTag;
            if (!excluded) {
                hasPositiveType = true;
            }

            if (!tagId.startsWith("#")) {
                continue;
            }

            Identifier identifier = Identifier.tryParse(tagId.substring(1));
            if (identifier == null) {
                continue;
            }

            TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
            if (state.is(tag)) {
                if (excluded) {
                    return false;
                }
                matchesType = true;
            }
        }

        if (hasPositiveType && !matchesType) {
            return false;
        }

        for (String configuredState : blockStates) {
            boolean excluded = configuredState.startsWith("!");
            String stateRule = excluded ? configuredState.substring(1) : configuredState;
            boolean present = state.getValues()
                    .map(Object::toString)
                    .anyMatch(stateRule::equals);
            if (excluded && present || !excluded && !present) {
                return false;
            }
        }

        return true;
    }

    public double sittingHeight() {
        return sittingHeight;
    }

    private static List<String> strings(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw invalid(key + " must be an array of strings");
        }

        List<String> values = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalid(key + " must be an array of strings");
            }
            values.add(value.getAsString());
        }
        return values;
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
        if (!Double.isFinite(value)) {
            throw invalid(key + " must be finite");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid sitting configuration: " + message);
    }
}

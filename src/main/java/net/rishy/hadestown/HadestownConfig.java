package net.rishy.hadestown;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HadestownConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hadestown");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JSON_FILE = "hadestown.json";
    private static final List<String> LEGACY_FILES = List.of("hadestown.yml", "hadestown.yaml", "config.yml");
    private static final String DEFAULT_RESOURCE = "/config/hadestown.json";

    private final String worldId;
    private final String developer;
    private final List<String> crew;
    private final List<String> cast;
    private final BlockPos infoSignPos;
    private final BlockPos reverseNoteBlockPos;
    private final BlockPos forwardNoteBlockPos;
    private final SetSettings sets;

    private HadestownConfig(
            String worldId,
            String developer,
            List<String> crew,
            List<String> cast,
            BlockPos infoSignPos,
            BlockPos reverseNoteBlockPos,
            BlockPos forwardNoteBlockPos,
            SetSettings sets
    ) {
        this.worldId = worldId;
        this.developer = developer;
        this.crew = List.copyOf(crew);
        this.cast = List.copyOf(cast);
        this.infoSignPos = infoSignPos;
        this.reverseNoteBlockPos = reverseNoteBlockPos;
        this.forwardNoteBlockPos = forwardNoteBlockPos;
        this.sets = sets;
    }

    public static HadestownConfig load() {
        return load(FabricLoader.getInstance().getConfigDir());
    }

    static HadestownConfig load(Path configDirectory) {
        Path path = configDirectory.resolve(JSON_FILE);

        try {
            Files.createDirectories(configDirectory);
            if (Files.notExists(path)) {
                Path legacyPath = findLegacyConfig(configDirectory);
                if (legacyPath != null) {
                    convertYamlToJson(legacyPath, path);
                } else {
                    copyDefaultConfig(path);
                }
            }

            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    throw invalid("root must be a JSON object");
                }
                return parse(root.getAsJsonObject());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + path, e);
        }
    }

    private static Path findLegacyConfig(Path configDirectory) {
        for (String fileName : LEGACY_FILES) {
            Path path = configDirectory.resolve(fileName);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static void copyDefaultConfig(Path path) throws IOException {
        try (InputStream input = HadestownConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled default configuration " + DEFAULT_RESOURCE);
            }
            Files.copy(input, path);
        }
    }

    static void convertYamlToJson(Path source, Path target) throws IOException {
        JsonElement root;
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            Object yamlRoot = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            root = yamlValueToJson(yamlRoot);
        }

        if (!root.isJsonObject()) {
            throw invalid("legacy YAML root must be an object");
        }

        Files.writeString(
                target,
                GSON.toJson(root) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
        LOGGER.info("Converted legacy YAML configuration {} to {}", source.getFileName(), target.getFileName());
    }

    private static JsonElement yamlValueToJson(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw invalid("legacy YAML object keys must be strings");
                }
                object.add(key, yamlValueToJson(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray array = new JsonArray();
            for (Object element : iterable) {
                array.add(yamlValueToJson(element));
            }
            return array;
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Character character) {
            return new JsonPrimitive(character.toString());
        }
        if (value instanceof String string) {
            return new JsonPrimitive(string);
        }
        throw invalid("unsupported value in legacy YAML: " + value.getClass().getSimpleName());
    }

    private static HadestownConfig parse(JsonObject root) {
        JsonObject controls = requiredObject(root, "controls");
        JsonObject sets = requiredObject(root, "sets");

        HadestownConfig config = new HadestownConfig(
                string(root, "world", "minecraft:overworld"),
                requiredString(root, "dev"),
                strings(root, "crew"),
                strings(root, "cast"),
                blockPos(controls, "info-sign-pos"),
                blockPos(controls, "reverse-note-block-pos"),
                blockPos(controls, "forward-note-block-pos"),
                new SetSettings(
                        blockPos(sets, "stage-start-pos"),
                        blockPos(sets, "first-set-pos"),
                        integer(sets, "set-distance"),
                        integer(sets, "scene-spacing"),
                        integer(sets, "set-width"),
                        integer(sets, "set-length"),
                        integer(sets, "set-height"),
                        requiredString(sets, "set-direction"),
                        requiredString(sets, "scene-direction"),
                        integers(sets, "sets-per-scene")
                )
        );
        config.validate();
        return config;
    }

    private void validate() {
        if (worldId.isBlank()) {
            throw invalid("world must not be blank");
        }

        if (sets.setDistance < 0 || sets.sceneSpacing < 0) {
            throw invalid("set-distance and scene-spacing must be non-negative");
        }
        if (sets.setWidth <= 0 || sets.setLength <= 0 || sets.setHeight <= 0) {
            throw invalid("set dimensions must be positive");
        }
        if (sets.setsPerScene.isEmpty() || sets.setsPerScene.stream().anyMatch(value -> value <= 0)) {
            throw invalid("sets-per-scene must contain only positive values");
        }

        Scene.validateDirection(sets.setDirection);
        Scene.validateDirection(sets.sceneDirection);
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) {
            throw invalid(key + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String key) {
        String value = string(object, key, null);
        if (value == null || value.isBlank()) {
            throw invalid(key + " must be a non-blank string");
        }
        return value;
    }

    private static String string(JsonObject object, String key, String defaultValue) {
        JsonElement value = object.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid(key + " must be a string");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid(key + " must be an integer");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        int parsed = primitive.getAsInt();
        if (primitive.getAsDouble() != parsed) {
            throw invalid(key + " must be an integer");
        }
        return parsed;
    }

    private static BlockPos blockPos(JsonObject object, String key) {
        List<Integer> values = integers(object, key);
        if (values.size() != 3) {
            throw invalid(key + " must contain exactly three coordinates");
        }
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    private static List<Integer> integers(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw invalid(key + " must be an array of integers");
        }

        List<Integer> values = new ArrayList<>();
        JsonArray array = value.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw invalid(key + " must be an array of integers");
            }
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            int parsed = primitive.getAsInt();
            if (primitive.getAsDouble() != parsed) {
                throw invalid(key + " must be an array of integers");
            }
            values.add(parsed);
        }
        return values;
    }

    private static List<String> strings(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null) {
            return List.of();
        }
        if (!value.isJsonArray()) {
            throw invalid(key + " must be an array of strings");
        }

        List<String> values = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw invalid(key + " must be an array of strings");
            }
            values.add(element.getAsString());
        }
        return values;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid Hadestown configuration: " + message);
    }

    public String worldId() {
        return worldId;
    }

    public String developer() {
        return developer;
    }

    public List<String> crew() {
        return crew;
    }

    public List<String> cast() {
        return cast;
    }

    public BlockPos infoSignPos() {
        return infoSignPos;
    }

    public BlockPos reverseNoteBlockPos() {
        return reverseNoteBlockPos;
    }

    public BlockPos forwardNoteBlockPos() {
        return forwardNoteBlockPos;
    }

    public SetSettings sets() {
        return sets;
    }

    public record SetSettings(
            BlockPos stageStartPos,
            BlockPos firstSetPos,
            int setDistance,
            int sceneSpacing,
            int setWidth,
            int setLength,
            int setHeight,
            String setDirection,
            String sceneDirection,
            List<Integer> setsPerScene
    ) {
        public SetSettings {
            setsPerScene = List.copyOf(setsPerScene);
        }
    }
}

package net.rishy.hadestown;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HadestownConfigTest {
    @Test
    void convertsLegacyYamlAndKeepsTheSource(@TempDir Path configDirectory) throws IOException {
        Path yaml = configDirectory.resolve("hadestown.yml");
        Files.writeString(yaml, """
                dev: "developer"
                crew: [crew]
                cast: [actor]
                controls:
                  info-sign-pos: [-1, -2, -3]
                  reverse-note-block-pos: [-4, -5, -6]
                  forward-note-block-pos: [-7, -8, -9]
                sets:
                  stage-start-pos: [1, 2, 3]
                  first-set-pos: [4, 5, 6]
                  set-distance: 1
                  scene-spacing: 2
                  set-width: 3
                  set-length: 4
                  set-height: 5
                  sets-per-scene: [1, 2]
                  set-direction: "-x"
                  scene-direction: "z"
                """, StandardCharsets.UTF_8);

        HadestownConfig config = HadestownConfig.load(configDirectory);
        Path json = configDirectory.resolve("hadestown.json");

        assertEquals("developer", config.developer());
        assertEquals(List.of("actor"), config.cast());
        assertEquals(new BlockPos(-1, -2, -3), config.infoSignPos());
        assertEquals(List.of(1, 2), config.sets().setsPerScene());
        assertTrue(Files.exists(json));
        assertTrue(Files.exists(yaml));

        JsonObject converted = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        assertEquals("developer", converted.get("dev").getAsString());
        assertEquals("actor", converted.getAsJsonArray("cast").get(0).getAsString());
        assertEquals(2, converted.getAsJsonObject("sets").getAsJsonArray("sets-per-scene").size());
    }
}

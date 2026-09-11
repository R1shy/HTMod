package net.rishy.hadestown;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hadestown implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hadestown");

    @Override
    public void onInitialize() {
        HadestownConfig config = HadestownConfig.load();
        new HadestownRuntime(config).register();
        LOGGER.info("Hadestown initialized");
        LOGGER.info("Got SPS Layout of: {}", config.sets().setsPerScene());
    }
}

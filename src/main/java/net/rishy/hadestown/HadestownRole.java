package net.rishy.hadestown;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum HadestownRole {
    DEVELOPER("[DEV] ", ChatFormatting.RED),
    CAST("[CAST] ", ChatFormatting.GOLD),
    CREW("[CREW] ", ChatFormatting.DARK_GREEN);

    private final String prefix;
    private final ChatFormatting color;

    HadestownRole(String prefix, ChatFormatting color) {
        this.prefix = prefix;
        this.color = color;
    }

    public Component displayName(String name) {
        return Component.literal(prefix).withStyle(color).append(Component.literal(name));
    }

    public Component chatMessage(String name, Component message) {
        return Component.literal(prefix + name + ": ")
                .withStyle(color)
                .append(message.copy().withStyle(ChatFormatting.WHITE));
    }
}

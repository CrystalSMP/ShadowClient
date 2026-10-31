package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public class CoordinatesModule extends TextHudModule {
    private final BooleanSetting showDirection = add(new BooleanSetting("direction", "Himmelsrichtung", true));

    public CoordinatesModule() {
        super("coordinates", "Koordinaten", "Zeigt deine Position (XYZ)", true, 4, 36);
    }

    @Override
    protected String getText(MinecraftClient client) {
        PlayerEntity p = client.player;
        if (p == null) return null;
        String text = String.format("XYZ %d / %d / %d", p.getBlockX(), p.getBlockY(), p.getBlockZ());
        if (showDirection.get()) {
            text += "  " + directionName(p.getHorizontalFacing().asString());
        }
        return text;
    }

    private static String directionName(String facing) {
        return switch (facing) {
            case "north" -> "N";
            case "south" -> "S";
            case "east" -> "O";
            case "west" -> "W";
            default -> facing;
        };
    }
}

package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/** Lichtlevel am eigenen Standort (unter 1 Blocklicht können Monster spawnen). */
public class LightLevelModule extends TextHudModule {
    public LightLevelModule() {
        super("light_level", "Lichtlevel", "Block- und Himmelslicht an deiner Position", false, 4, 196);
    }

    @Override
    protected String getText(MinecraftClient c) {
        if (c.player == null || c.world == null) return null;
        BlockPos pos = c.player.getBlockPos();
        int block = c.world.getLightLevel(LightType.BLOCK, pos);
        int sky = c.world.getLightLevel(LightType.SKY, pos);
        return "Licht " + block + (block == 0 ? " ⚠" : "") + "  (Himmel " + sky + ")";
    }
}

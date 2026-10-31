package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;

/** Blendet das Scoreboard rechts aus (siehe InGameHudMixin). */
public class HideScoreboardModule extends Module {
    public HideScoreboardModule() {
        super("hide_scoreboard", "Scoreboard ausblenden", "Versteckt die Scoreboard-Seitenleiste", Category.VISUAL, false);
    }
}

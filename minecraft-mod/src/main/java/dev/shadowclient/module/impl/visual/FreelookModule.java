package dev.shadowclient.module.impl.visual;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Freelook: Taste halten (Standard: Linke Alt-Taste) und die Kamera frei um den Spieler drehen,
 * während Blick- und Laufrichtung gleich bleiben. Siehe EntityMixin und CameraMixin.
 */
public class FreelookModule extends Module {
    public final BooleanSetting thirdPerson = add(new BooleanSetting("third_person", "In 3. Person wechseln", true));

    private boolean active;
    private float yaw, pitch;
    private Perspective previousPerspective;

    public FreelookModule() {
        super("freelook", "Freelook", "Taste halten (Standard: Linke Alt) und frei umschauen", Category.VISUAL, false);
    }

    public boolean isActive() { return active && isEnabled(); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    @Override
    public void onTick(MinecraftClient client) {
        boolean held = ShadowClient.freelookKey().isPressed() && client.currentScreen == null && client.player != null;
        if (held && !active) {
            active = true;
            yaw = client.player.getYaw();
            pitch = client.player.getPitch();
            previousPerspective = client.options.getPerspective();
            if (thirdPerson.get() && previousPerspective.isFirstPerson()) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        } else if (!held && active) {
            stop(client);
        }
    }

    /** Mausbewegung auf die Freelook-Kamera umleiten (gleiche Empfindlichkeit wie Vanilla). */
    public void addRotation(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) cursorDeltaX * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) cursorDeltaY * 0.15f));
    }

    private void stop(MinecraftClient client) {
        active = false;
        if (previousPerspective != null) {
            client.options.setPerspective(previousPerspective);
            previousPerspective = null;
        }
    }

    @Override
    protected void onDisable() {
        if (active) stop(MinecraftClient.getInstance());
    }
}

package dev.shadowclient;

import dev.shadowclient.config.ConfigManager;
import dev.shadowclient.gui.ClickGuiScreen;
import dev.shadowclient.hud.HudRenderer;
import dev.shadowclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import dev.shadowclient.util.CombatTracker;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt der ShadowClient-Mod. Rein clientseitig: Menü und HUD-Anzeigen,
 * keine Änderungen an Pakete oder Serverdaten.
 */
public class ShadowClient implements ClientModInitializer {
    public static final String MOD_ID = "shadowclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("ShadowClient");

    /** Tastenkategorie (seit 1.21.9 ein Objekt statt String; darf nur einmal erstellt werden). */
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static KeyBinding menuKey;
    private static KeyBinding zoomKey;
    private static KeyBinding freelookKey;
    private static KeyBinding friendsKey;
    private static KeyBinding waypointKey;

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager(moduleManager);
        configManager.load();
        dev.shadowclient.online.PresenceWriter.register();

        // Standard: Rechts-Shift. Änderbar unter Optionen → Steuerung → ShadowClient.
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shadowclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shadowclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shadowclient.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

        friendsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shadowclient.friends", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

        waypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shadowclient.waypoint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));

        // Chat: Filter & Auto-GG (nur lesen bzw. ausblenden)
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || dev.shadowclient.module.impl.comfort.ChatFilterModule.INSTANCE == null
                        || dev.shadowclient.module.impl.comfort.ChatFilterModule.INSTANCE.allow(message.getString()));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) ->
                dev.shadowclient.module.impl.comfort.ChatFilterModule.INSTANCE == null
                        || dev.shadowclient.module.impl.comfort.ChatFilterModule.INSTANCE.allow(message.getString()));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && dev.shadowclient.module.impl.comfort.AutoGgModule.INSTANCE != null)
                dev.shadowclient.module.impl.comfort.AutoGgModule.INSTANCE.onMessage(message.getString());
        });

        dev.shadowclient.online.SocialCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            moduleManager.tick(client);
            while (waypointKey.wasPressed()) {
                var wp = moduleManager.waypoints();
                if (wp == null || !wp.isEnabled() || client.player == null) continue;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) dev.shadowclient.module.impl.comfort.Waypoints.removeNearest(client);
                else wp.addHere(client);
            }
            while (friendsKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new dev.shadowclient.gui.FriendsScreen());
            }
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ClickGuiScreen());
                }
            }
        });

        // Seit 1.21.6: HUD-Elemente über die HudElementRegistry
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), (drawContext, tickCounter) -> HudRenderer.render(drawContext));
        // Eigene Treffer für Combo, Reichweite und Target-HUD (nur lesen, Aktion läuft normal weiter)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            CombatTracker.onAttack(player, entity, hitResult);
            if (player == net.minecraft.client.MinecraftClient.getInstance().player) dev.shadowclient.module.impl.hud.KillCounterModule.onAttack(entity);
            return ActionResult.PASS;
        });

        // Freunde-Button im Hauptmenü, rechts neben „Minecraft Realms“
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                int realmsY = scaledHeight / 4 + 48 + 48;
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("✦"),
                                b -> client.setScreen(new dev.shadowclient.gui.FriendsScreen(screen)))
                        .dimensions(scaledWidth / 2 + 128, realmsY, 20, 20)
                        .tooltip(Tooltip.of(Text.literal("ShadowClient Freunde")))
                        .build());
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            moduleManager.restoreVanillaOptions();
            configManager.save();
        });

        LOGGER.info("ShadowClient geladen ({} Module)", moduleManager.all().size());
    }

    public static ModuleManager modules() { return moduleManager; }
    public static ConfigManager config() { return configManager; }
    public static KeyBinding menuKey() { return menuKey; }
    public static KeyBinding zoomKey() { return zoomKey; }
    public static KeyBinding freelookKey() { return freelookKey; }
    public static KeyBinding friendsKey() { return friendsKey; }
}

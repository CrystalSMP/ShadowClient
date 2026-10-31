package dev.shadowclient.util;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Zählt Klicks pro Sekunde über Flankenerkennung der Angriffs-/Benutzen-Taste.
 * Wird jedes Frame abgefragt, daher genau genug bei normalen FPS.
 */
public final class ClickTracker {
    private static final Deque<Long> left = new ArrayDeque<>();
    private static final Deque<Long> right = new ArrayDeque<>();
    private static boolean leftDown, rightDown;

    private ClickTracker() {}

    public static void update(MinecraftClient client) {
        long now = System.currentTimeMillis();
        boolean l = client.options.attackKey.isPressed();
        boolean r = client.options.useKey.isPressed();
        if (l && !leftDown) left.addLast(now);
        if (r && !rightDown) right.addLast(now);
        leftDown = l;
        rightDown = r;
        while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
        while (!right.isEmpty() && now - right.peekFirst() > 1000) right.pollFirst();
    }

    public static int leftCps() { return left.size(); }
    public static int rightCps() { return right.size(); }
}

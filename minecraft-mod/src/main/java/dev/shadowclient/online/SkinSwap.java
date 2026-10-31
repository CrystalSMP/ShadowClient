package dev.shadowclient.online;

import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

/**
 * Ersetzt in den Skin-Daten eines Spielers das Cape.
 * Per Reflection, weil Mojang den Aufbau dieser Daten zwischen 1.21.x-Versionen mehrfach geändert hat
 * (früher Identifier, ab 1.21.9 eigene Asset-Objekte). So bleibt die Mod robust.
 */
public final class SkinSwap {
    private static Class<?> cachedType;
    private static RecordComponent[] comps;
    private static Constructor<?> ctor;
    private static int capeIdx = -1, bodyIdx = -1;
    private static boolean broken;

    private SkinSwap() {}

    public static Object withCape(Object skin, Identifier cape) {
        if (broken || skin == null || !skin.getClass().isRecord()) return null;
        try {
            if (cachedType != skin.getClass()) init(skin.getClass());
            if (capeIdx < 0) return null;
            Object[] args = new Object[comps.length];
            for (int i = 0; i < comps.length; i++) args[i] = comps[i].getAccessor().invoke(skin);
            Class<?> capeType = comps[capeIdx].getType();
            if (capeType == Identifier.class) {
                args[capeIdx] = cape;
            } else {
                Object body = bodyIdx >= 0 ? args[bodyIdx] : null;
                Object asset = makeAsset(body != null ? body.getClass() : null, capeType, cape);
                if (asset == null) return null;
                args[capeIdx] = asset;
            }
            return ctor.newInstance(args);
        } catch (Throwable t) {
            broken = true;  // einmal scheitern reicht – dann einfach Vanilla-Capes
            return null;
        }
    }

    private static void init(Class<?> type) throws NoSuchMethodException {
        cachedType = type;
        comps = type.getRecordComponents();
        Class<?>[] types = new Class<?>[comps.length];
        capeIdx = bodyIdx = -1;
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            String n = comps[i].getName().toLowerCase();
            if (n.contains("cape")) capeIdx = i;
            else if (bodyIdx < 0 && (n.contains("body") || n.equals("texture") || n.contains("skin"))) bodyIdx = i;
        }
        ctor = type.getDeclaredConstructor(types);
        ctor.setAccessible(true);
    }

    /** Baut ein Asset-Objekt vom Typ des Skins, in dem alle Identifier-Felder auf das Cape zeigen. */
    private static Object makeAsset(Class<?> concrete, Class<?> declared, Identifier cape) throws Exception {
        Class<?> c = concrete != null && declared.isAssignableFrom(concrete) ? concrete : declared;
        if (!c.isRecord()) return null;
        RecordComponent[] rc = c.getRecordComponents();
        Class<?>[] t = new Class<?>[rc.length];
        Object[] a = new Object[rc.length];
        for (int i = 0; i < rc.length; i++) {
            t[i] = rc[i].getType();
            if (t[i] == Identifier.class) a[i] = cape;
            else if (t[i] == String.class) a[i] = cape.toString();
            else if (t[i] == boolean.class) a[i] = false;
            else return null;
        }
        Constructor<?> k = c.getDeclaredConstructor(t);
        k.setAccessible(true);
        return k.newInstance(a);
    }
}

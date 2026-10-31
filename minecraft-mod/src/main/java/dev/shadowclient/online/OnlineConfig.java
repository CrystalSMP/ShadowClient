package dev.shadowclient.online;

/**
 * Adresse des ShadowClient-Backends (Supabase Edge Function).
 * Beides ist öffentlich und kein Geheimnis: Der Publishable Key erlaubt keinen direkten
 * Tabellenzugriff (RLS), geschrieben wird nur über die Edge Function mit Mojang-Prüfung.
 */
public final class OnlineConfig {
    private static final String BACKEND_URL = "https://qshyrzvaspluttbvicnw.supabase.co/functions/v1/shadowclient";
    private static final String PUBLISHABLE_KEY = "sb_publishable__aSiHs7_jEfjOGwZxQ0Wmw_mMzF8k7L";

    private OnlineConfig() {}

    public static String backendUrl() { return BACKEND_URL; }
    public static String anonKey() { return PUBLISHABLE_KEY; }
}

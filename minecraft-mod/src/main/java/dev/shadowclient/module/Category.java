package dev.shadowclient.module;

public enum Category {
    HUD("HUD"),
    VISUAL("Optik"),
    UTILITY("Komfort"),
    ONLINE("Online"),
    CLIENT("Client");

    public final String displayName;

    Category(String displayName) { this.displayName = displayName; }
}

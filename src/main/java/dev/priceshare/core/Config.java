package dev.priceshare.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/priceshare/config.json. Sharing is off until the player answers the prompt with yes.
 *
 * <ul>
 *   <li>{@code asked}: the prompt has been answered (either way), so it is never shown again</li>
 *   <li>{@code enabled}: share at all</li>
 *   <li>{@code showOnSite}: let DonutMarket show the player's name on its contributors list</li>
 * </ul>
 * The upload token lives next to it in {@code token}, with the UUID of the account it was issued
 * to, so switching accounts in the launcher never uploads under the old one. Deleting the folder
 * resets everything.
 */
public final class Config {
    private final Path dir;
    private volatile boolean asked;
    private volatile boolean enabled;
    private volatile boolean showOnSite;
    private volatile String token;
    private volatile String tokenUuid;

    public Config(Path dir) {
        this.dir = dir;
        load();
    }

    public boolean asked() { return asked; }
    public boolean enabled() { return enabled; }
    public boolean showOnSite() { return showOnSite; }
    /** The stored token if it was issued to {@code uuid} (no dashes), else null. */
    public String tokenFor(String uuid) {
        String t = token;
        return t != null && uuid != null && uuid.equalsIgnoreCase(tokenUuid) ? t : null;
    }

    /** The answer to the one-time prompt. */
    public synchronized void answer(boolean yes) {
        asked = true;
        enabled = yes;
        save();
    }

    public synchronized void setEnabled(boolean on) {
        asked = true;
        enabled = on;
        save();
    }

    public synchronized void setShowOnSite(boolean on) {
        showOnSite = on;
        save();
    }

    /** Stores a token for the account {@code uuid}; a null token forgets it. */
    public synchronized void setToken(String uuid, String t) {
        token = t;
        tokenUuid = t == null ? null : uuid;
        Path f = dir.resolve("token");
        try {
            if (t == null) {
                Files.deleteIfExists(f);
            } else {
                Files.createDirectories(dir);
                Files.writeString(f, "{\"uuid\":" + Json.quote(uuid) + ",\"token\":" + Json.quote(t) + "}\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.warn("Could not save the upload token: " + e);
        }
    }

    private void load() {
        try {
            Path f = dir.resolve("config.json");
            if (Files.exists(f)) {
                Object o = Json.parse(Files.readString(f, StandardCharsets.UTF_8));
                asked = Json.bool(o, "asked", false);
                enabled = asked && Json.bool(o, "enabled", false);
                showOnSite = Json.bool(o, "showOnSite", false);
            }
        } catch (Exception e) {
            // A broken file means sharing stays off and the prompt shows again.
            Log.warn("Could not read config.json, sharing stays off: " + e);
            asked = false;
            enabled = false;
            showOnSite = false;
        }
        try {
            Path t = dir.resolve("token");
            if (Files.exists(t)) {
                // {"uuid":..,"token":..}. Anything else (an older plain token) has no owner and is
                // treated as no token, so the mod logs in again.
                Object o = Json.parse(Files.readString(t, StandardCharsets.UTF_8));
                token = Json.str(o, "token");
                tokenUuid = Json.str(o, "uuid");
                if (token == null || tokenUuid == null) token = tokenUuid = null;
            }
        } catch (Exception e) {
            token = null;
            tokenUuid = null;
        }
    }

    private void save() {
        String json = "{\n  \"asked\": " + asked + ",\n  \"enabled\": " + enabled + ",\n  \"showOnSite\": " + showOnSite + "\n}\n";
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("config.json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn("Could not save config.json: " + e);
        }
    }
}

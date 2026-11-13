package dev.priceshare.core;

import java.nio.file.Path;

/**
 * The state the game-side code works with. The upload thread is only started the first time a
 * page is actually shared, so with sharing off (or off DonutSMP) the mod does nothing at all.
 */
public final class PriceShare {
    private PriceShare() {}

    public static final String DEFAULT_URL = "https://donutmarket.cc";
    /** -Dpriceshare.url=http://localhost:3000 for testing against a local site. */
    public static final String URL = System.getProperty("priceshare.url", DEFAULT_URL);
    /** -Dpriceshare.anyServer=true reads market menus on any server (testing only). */
    public static final boolean ANY_SERVER = Boolean.getBoolean("priceshare.anyServer");

    private static Config config;
    private static Uploader uploader;
    private static Uploader.Session session;
    private static String modVersion = "?";
    private static String mcVersion = "?";
    public static final Gate GATE = new Gate();
    /** A market menu opened before the player answered the prompt: ask once it closes. */
    public static boolean promptPending;

    public static void init(Path configDir, String modVer, String mcVer, Uploader.Session s) {
        config = new Config(configDir);
        modVersion = modVer;
        mcVersion = mcVer;
        session = s;
        Log.info("Loaded. Sharing is " + (config.enabled() ? "on" : "off") + (ANY_SERVER ? " (any server: testing)" : ""));
    }

    public static Config config() {
        return config;
    }

    public static void share(Page page) {
        if (!config.enabled()) return;
        uploader().offer(page);
    }

    public static void setEnabled(boolean on) {
        config.setEnabled(on);
        if (!on && uploader != null) uploader.clear();
    }

    /** A line for the settings screen. */
    public static String status() {
        if (!config.enabled()) return "Sharing is off";
        return uploader == null ? "Nothing shared yet this session" : uploader.status();
    }

    private static synchronized Uploader uploader() {
        if (uploader == null) uploader = new Uploader(config, URL, modVersion, mcVersion, session);
        return uploader;
    }
}

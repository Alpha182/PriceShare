package dev.priceshare.core;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decides when a menu page is captured, and stops the same page going out twice.
 *
 * <ul>
 *   <li>A page is read once its slots have stopped changing for 2 ticks (they arrive in a packet
 *       after the menu opens, sometimes in several).</li>
 *   <li>The same page (kind, title and slots) is not sent again within 60 s.</li>
 *   <li>Quick Buy refreshes its prices live, so it is read at most once every 30 s.</li>
 * </ul>
 * Not thread-safe: used from the client tick only.
 */
public final class Gate {
    public static final long SAME_PAGE_MS = 60_000;
    public static final long SHOP_EVERY_MS = 30_000;
    private static final int STABLE_TICKS = 2;
    private static final int REMEMBER = 128;

    private int lastSignature;
    private Object lastScreen;
    private int stable;
    private long lastShopAt = Long.MIN_VALUE / 2;
    /** Recently sent pages by content hash, oldest first. */
    private final LinkedHashMap<Long, Long> sent = new LinkedHashMap<>();

    /**
     * Called every tick while a market menu is open. True on the one tick its contents have
     * just become stable: the moment to read it.
     */
    public boolean settled(Object screen, int signature) {
        if (screen != lastScreen || signature != lastSignature) {
            lastScreen = screen;
            lastSignature = signature;
            stable = 0;
            return false;
        }
        return ++stable == STABLE_TICKS;
    }

    /** No market menu is open. */
    public void reset() {
        lastScreen = null;
        stable = 0;
    }

    /** Whether a read page should go out now; if so it's remembered as sent. */
    public boolean admit(Page page, long now) {
        if (page.kind() == Menus.Kind.SHOP && now - lastShopAt < SHOP_EVERY_MS) {
            return false;
        }
        long h = hash(page.contentKey());
        Iterator<Map.Entry<Long, Long>> it = sent.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= SAME_PAGE_MS) it.remove();
            else break;
        }
        if (sent.containsKey(h)) {
            return false;
        }
        sent.put(h, now);
        while (sent.size() > REMEMBER) {
            sent.remove(sent.keySet().iterator().next());
        }
        if (page.kind() == Menus.Kind.SHOP) lastShopAt = now;
        return true;
    }

    /** 64-bit FNV-1a: collisions between two real pages are not a concern at this size. */
    static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}

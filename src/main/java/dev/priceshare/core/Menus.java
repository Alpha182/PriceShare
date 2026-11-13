package dev.priceshare.core;

import java.util.regex.Pattern;

/**
 * Which menus count as market menus, and which servers. Everything else is ignored: the mod does
 * nothing at all off DonutSMP, and on DonutSMP it only reads these menus (and the leaderboard
 * dialogs, see {@link Board}).
 */
public final class Menus {
    private Menus() {}

    public enum Kind {
        AH("ah"), ORDERS("orders"), SHOP("shop"), BALTOP("baltop"), BOUNTY("bounty"),
        /** A leaderboard dialog: not a chest menu, so never returned by {@link #classify}. */
        BOARD("board");

        public final String wire;

        Kind(String wire) {
            this.wire = wire;
        }
    }

    private static final Pattern AH = Pattern.compile("^Auction \\(Page \\d{1,5}\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDERS = Pattern.compile("^Orders \\(Page \\d{1,5}\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOP = Pattern.compile("^Quick Buy$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BALTOP = Pattern.compile("^Most Money \\(Page \\d{1,5}\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOUNTY = Pattern.compile("^Bounties \\(Page \\d{1,5}\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DONUT_HOST = Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)*donutsmp\\.net\\.?(?::\\d{1,5})?$");

    /** The kind of market menu a plain-text title belongs to, or null if it isn't one. */
    public static Kind classify(String title) {
        if (title == null) return null;
        String t = Slot.plain(title);
        if (AH.matcher(t).matches()) return Kind.AH;
        if (ORDERS.matcher(t).matches()) return Kind.ORDERS;
        if (SHOP.matcher(t).matches()) return Kind.SHOP;
        if (BALTOP.matcher(t).matches()) return Kind.BALTOP;
        if (BOUNTY.matcher(t).matches()) return Kind.BOUNTY;
        return null;
    }

    /** True for donutsmp.net and its subdomains, with or without a port. */
    public static boolean isDonut(String address) {
        return address != null && DONUT_HOST.matcher(address.trim()).matches();
    }
}

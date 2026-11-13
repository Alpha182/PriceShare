package dev.priceshare.core;

import java.util.ArrayList;
import java.util.List;

/**
 * DonutSMP's full leaderboards (/leaderboard) are server-sent dialogs, not chest menus: a list of
 * plain messages, one per player. On Donut each looks like text "#1", then sibling parts
 * [head/space bits, "Name", "  -  ", "70M"], and a custom click event whose payload names the
 * player. The game side pulls those strings out; this turns them into rows.
 */
public final class Board {
    private Board() {}

    /** A leaderboard is a dialog with more than this many messages; smaller ones are ignored. */
    public static final int MIN_ROWS = 11;
    public static final int MAX_ROWS = 300;
    public static final int MAX_TEXT = 100;
    private static final int MAX_EXTRA = 16;

    /**
     * One message of the dialog.
     *
     * @param text  the message's own text, plain
     * @param extra the plain text of each sibling part, in order
     * @param name  the "name" in the message's custom click payload, or null
     */
    public record Row(String text, List<String> extra, String name) {
        public void write(StringBuilder out) {
            out.append("{\"text\":");
            Json.string(out, text);
            out.append(",\"extra\":[");
            for (int i = 0; i < extra.size(); i++) {
                if (i > 0) out.append(',');
                Json.string(out, extra.get(i));
            }
            out.append("],\"name\":");
            Json.string(out, name);
            out.append('}');
        }
    }

    /** A row from the raw strings: plain text (no § codes, small caps folded), lengths capped. */
    public static Row row(String text, List<String> extra, String name) {
        List<String> parts = new ArrayList<>(Math.min(extra.size(), MAX_EXTRA));
        for (int i = 0; i < extra.size() && i < MAX_EXTRA; i++) parts.add(cap(extra.get(i)));
        String n = cap(name);
        return new Row(cap(text), List.copyOf(parts), n == null || n.isEmpty() ? null : n);
    }

    /** The page to send for a dialog, or null if it has too few rows to be a leaderboard. */
    public static Page page(long at, String title, List<Row> rows) {
        if (rows.size() < MIN_ROWS) return null;
        List<Row> kept = rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows;
        return Page.board(at, cap(title), List.copyOf(kept));
    }

    static String cap(String s) {
        if (s == null) return null;
        String p = Slot.plain(s);
        return p.length() > MAX_TEXT ? p.substring(0, MAX_TEXT) : p;
    }
}

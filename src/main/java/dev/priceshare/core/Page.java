package dev.priceshare.core;

import java.util.List;

/**
 * One captured page: what kind, when, its title, and either the slots of a chest menu or the
 * rows of a leaderboard dialog ({@code kind} "board").
 */
public record Page(Menus.Kind kind, long at, String title, List<Slot> slots, List<Board.Row> rows) {

    /** A chest menu page. */
    public Page(Menus.Kind kind, long at, String title, List<Slot> slots) {
        this(kind, at, title, slots, null);
    }

    /** A leaderboard dialog. */
    public static Page board(long at, String title, List<Board.Row> rows) {
        return new Page(Menus.Kind.BOARD, at, title, null, rows);
    }

    public void write(StringBuilder out) {
        out.append("{\"kind\":");
        Json.string(out, kind.wire);
        out.append(",\"at\":").append(at);
        out.append(",\"title\":");
        Json.string(out, title);
        writeContent(out);
        out.append('}');
    }

    private void writeContent(StringBuilder out) {
        if (rows != null) {
            out.append(",\"rows\":[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) out.append(',');
                rows.get(i).write(out);
            }
        } else {
            out.append(",\"slots\":[");
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) out.append(',');
                slots.get(i).write(out);
            }
        }
        out.append(']');
    }

    /** Kind, title and content: what makes two pages "the same page" for de-duplication. */
    public String contentKey() {
        StringBuilder b = new StringBuilder(kind.wire).append('|').append(title).append('|');
        writeContent(b);
        return b.toString();
    }
}

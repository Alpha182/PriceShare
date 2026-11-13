package dev.priceshare;

import dev.priceshare.core.Board;
import dev.priceshare.core.Page;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a server-sent dialog's messages into leaderboard rows. Only looks at the dialog's data:
 * no button is pressed and nothing is sent to the server.
 */
public final class Boards {
    private Boards() {}

    /** The leaderboard page in this dialog, or null if it isn't one (10 messages or fewer). */
    public static Page read(Dialog dialog, long at) {
        List<DialogBody> body = dialog.common().body();
        int messages = 0;
        for (DialogBody b : body) if (b instanceof PlainMessage) messages++;
        if (messages < Board.MIN_ROWS) return null;
        List<Board.Row> rows = new ArrayList<>(Math.min(messages, Board.MAX_ROWS));
        for (DialogBody b : body) {
            if (!(b instanceof PlainMessage pm)) continue;
            if (rows.size() >= Board.MAX_ROWS) break;
            Component c = pm.contents();
            List<String> extra = new ArrayList<>(c.getSiblings().size());
            for (Component s : c.getSiblings()) extra.add(s.getString());
            // plainCopy keeps the message's own contents without its siblings or style.
            rows.add(Board.row(c.plainCopy().getString(), extra, clickName(c)));
        }
        return Board.page(at, dialog.common().title().getString(), rows);
    }

    /** Messages in the dialog: a cheap check before reading it. */
    public static int messages(Dialog dialog) {
        int n = 0;
        for (DialogBody b : dialog.common().body()) if (b instanceof PlainMessage) n++;
        return n;
    }

    /** The "name" in a custom click event's compound payload, on the message or one of its parts. */
    private static String clickName(Component c) {
        String n = nameIn(c.getStyle().getClickEvent());
        if (n != null) return n;
        for (Component s : c.getSiblings()) {
            n = nameIn(s.getStyle().getClickEvent());
            if (n != null) return n;
        }
        return null;
    }

    private static String nameIn(ClickEvent e) {
        if (e instanceof ClickEvent.Custom custom) {
            Tag payload = custom.payload().orElse(null);
            if (payload instanceof CompoundTag tag) return tag.getString("name").orElse(null);
        }
        return null;
    }
}

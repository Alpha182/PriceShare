package dev.priceshare.core;

import java.util.List;
import java.util.Map;

/**
 * One slot of a market menu, as sent to the site (see PROTOCOL.md).
 *
 * @param slot         index in the chest part of the menu
 * @param id           item id without the "minecraft:" namespace
 * @param count        stack size
 * @param name         display name as plain text
 * @param lore         lore lines as plain text, empty lines kept
 * @param enchantments enchantments and stored (book) enchantments, id without namespace to level
 * @param potion       potion id for potions and tipped arrows, else null
 */
public record Slot(int slot, String id, int count, String name, List<String> lore, Map<String, Integer> enchantments, String potion) {

    public void write(StringBuilder out) {
        out.append("{\"slot\":").append(slot);
        out.append(",\"id\":");
        Json.string(out, id);
        out.append(",\"count\":").append(count);
        out.append(",\"name\":");
        Json.string(out, name);
        out.append(",\"lore\":[");
        for (int i = 0; i < lore.size(); i++) {
            if (i > 0) out.append(',');
            Json.string(out, lore.get(i));
        }
        out.append("],\"enchantments\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : enchantments.entrySet()) {
            if (!first) out.append(',');
            first = false;
            Json.string(out, e.getKey());
            out.append(':').append(e.getValue());
        }
        out.append("},\"potion\":");
        Json.string(out, potion);
        out.append('}');
    }

    /** Drops the namespace from a registry id: "minecraft:sharpness" becomes "sharpness". */
    public static String path(String id) {
        if (id == null) return null;
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ";
    private static final String ASCII = "abcdefghijklmnopqrstuvwyz";

    /**
     * Removes legacy formatting codes ("§a"), turns small-caps letters some servers style text
     * with back into plain letters (as the site's plainText does), and trims.
     */
    public static String plain(String s) {
        if (s == null) return null;
        String t = s.replaceAll("§.", "");
        StringBuilder b = null;
        for (int i = 0; i < t.length(); i++) {
            int k = SMALL_CAPS.indexOf(t.charAt(i));
            if (k >= 0) {
                if (b == null) b = new StringBuilder(t);
                b.setCharAt(i, ASCII.charAt(k));
            }
        }
        return (b == null ? t : b.toString()).trim();
    }
}

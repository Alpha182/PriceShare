package dev.priceshare.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private static Slot slot(int i, String id, String lore) {
        return new Slot(i, id, 1, id, List.of(lore), Map.of(), null);
    }

    @Test
    void titles() {
        assertEquals(Menus.Kind.AH, Menus.classify("Auction (Page 1)"));
        assertEquals(Menus.Kind.AH, Menus.classify("§fAuction (Page 702)"));
        assertEquals(Menus.Kind.ORDERS, Menus.classify("Orders (Page 3)"));
        assertEquals(Menus.Kind.SHOP, Menus.classify("Quick Buy"));
        assertNull(Menus.classify("Your Items"));
        assertNull(Menus.classify("Auction"));
        assertNull(Menus.classify("Ender Chest"));
        assertNull(Menus.classify("Auction (Page 1) - Bob's chest"));
        assertNull(Menus.classify(null));
        // Restyled titles: other capitalisation, small-caps letters.
        assertEquals(Menus.Kind.AH, Menus.classify("AUCTION (PAGE 4)"));
        assertEquals(Menus.Kind.AH, Menus.classify("ᴀᴜᴄᴛɪᴏɴ (ᴘᴀɢᴇ 2)"));
        assertEquals(Menus.Kind.ORDERS, Menus.classify("ᴏʀᴅᴇʀꜱ (Page 1)"));
        assertEquals(Menus.Kind.SHOP, Menus.classify("ǫᴜɪᴄᴋ ʙᴜʏ"));
        assertEquals(Menus.Kind.SHOP, Menus.classify("quick buy"));
        // Still the exact shape.
        assertNull(Menus.classify("ᴀᴜᴄᴛɪᴏɴ (ᴘᴀɢᴇ 2) chest"));
        assertNull(Menus.classify("My Quick Buy"));
    }

    @Test
    void leaderboardMenuTitles() {
        assertEquals(Menus.Kind.BALTOP, Menus.classify("Most Money (Page 1)"));
        assertEquals(Menus.Kind.BALTOP, Menus.classify("ᴍᴏꜱᴛ ᴍᴏɴᴇʏ (ᴘᴀɢᴇ 223)"));
        assertEquals(Menus.Kind.BALTOP, Menus.classify("MOST MONEY (PAGE 12)"));
        assertEquals(Menus.Kind.BOUNTY, Menus.classify("Bounties (Page 3)"));
        assertEquals(Menus.Kind.BOUNTY, Menus.classify("§cʙᴏᴜɴᴛɪᴇꜱ (Page 1)"));
        assertNull(Menus.classify("Most Money"));
        assertNull(Menus.classify("Bounties (Page 1) of Bob"));
        assertNull(Menus.classify("Most Money (Page 123456)"));
        assertNull(Menus.classify("Leaderboard"), "boards are dialogs, never chest menus");
    }

    /** Rows as DonutSMP's full leaderboard sends them: "#1", [head, name, " - ", value], click {name}. */
    private static List<Board.Row> donutRows(int n) {
        List<Board.Row> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            rows.add(Board.row("§e#" + i, List.of(" ", "Player" + i, "  -  ", "§a" + (70 - i) + "M"), "Player" + i));
        }
        return rows;
    }

    @Test
    void boardRows() {
        Board.Row r = Board.row("§e#1", List.of("§f ", "ʙᴏʙ", "  -  ", "70M"), "Bob");
        assertEquals("#1", r.text());
        assertEquals(List.of("", "bob", "-", "70M"), r.extra(), "plain: codes stripped, small caps folded, trimmed");
        assertEquals("Bob", r.name());
        assertNull(Board.row("#2", List.of(), "").name(), "an empty payload name is no name");
        assertNull(Board.row("#2", List.of(), null).name());
        String longText = "x".repeat(250);
        assertEquals(Board.MAX_TEXT, Board.row(longText, List.of(longText), longText).text().length());
        assertEquals(Board.MAX_TEXT, Board.row("#1", List.of(longText), null).extra().get(0).length());
    }

    @Test
    void boardPages() {
        assertNull(Board.page(0, "Kills", donutRows(10)), "10 messages or fewer is not a leaderboard");
        Page p = Board.page(5, "ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ", donutRows(11));
        assertNotNull(p);
        assertEquals(Menus.Kind.BOARD, p.kind());
        assertEquals("leaderboard", p.title());
        assertEquals(Board.MAX_ROWS, Board.page(0, "Money", donutRows(450)).rows().size(), "capped at 300 rows");

        StringBuilder b = new StringBuilder();
        p.write(b);
        Map<?, ?> json = (Map<?, ?>) Json.parse(b.toString());
        assertEquals("board", json.get("kind"));
        assertFalse(json.containsKey("slots"), "a board page has rows, not slots");
        Map<?, ?> row = (Map<?, ?>) ((List<?>) json.get("rows")).get(0);
        assertEquals("#1", row.get("text"));
        assertEquals(List.of("", "Player1", "-", "69M"), row.get("extra"));
        assertEquals("Player1", row.get("name"));
    }

    @Test
    void sameBoardOnlyOnceAMinute() {
        Gate g = new Gate();
        Page a = Board.page(0, "Money", donutRows(20));
        Page changed = Board.page(0, "Money", donutRows(21));
        assertTrue(g.admit(a, 1_000));
        assertFalse(g.admit(Board.page(0, "Money", donutRows(20)), 30_000), "same content within 60 s");
        assertTrue(g.admit(changed, 30_000));
        assertTrue(g.admit(a, 1_000 + Gate.SAME_PAGE_MS));
    }

    @Test
    void plainFoldsSmallCaps() {
        assertEquals("abcdefghijklmnopqrstuvwyz",
                Slot.plain("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ"));
        assertEquals("Seller: Bob_12 ▪ $1.2M", Slot.plain("  §aSeller: Bob_12 ▪ $1.2M "));
        assertEquals("", Slot.plain(""));
        assertNull(Slot.plain(null));
    }

    @Test
    void hosts() {
        assertTrue(Menus.isDonut("donutsmp.net"));
        assertTrue(Menus.isDonut("play.donutsmp.net:25565"));
        assertTrue(Menus.isDonut("DonutSMP.net."));
        assertFalse(Menus.isDonut("donutsmp.net.evil.com"));
        assertFalse(Menus.isDonut("notdonutsmp.net"));
        assertFalse(Menus.isDonut("hypixel.net"));
        assertFalse(Menus.isDonut(null));
    }

    @Test
    void jsonEscapesAndRoundTrips() {
        String tricky = "a\"b\\c\nd\te\u0001f g ▪ $1.2M";
        Slot s = new Slot(3, "diamond_sword", 1, tricky, List.of("", "$ 1T", tricky), Map.of("sharpness", 5), null);
        Page p = new Page(Menus.Kind.AH, 123L, "Auction (Page 1)", List.of(s));
        StringBuilder b = new StringBuilder();
        p.write(b);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(b.toString());
        assertEquals("ah", parsed.get("kind"));
        Map<?, ?> slot = (Map<?, ?>) ((List<?>) parsed.get("slots")).get(0);
        assertEquals(tricky, slot.get("name"));
        assertEquals(List.of("", "$ 1T", tricky), slot.get("lore"));
        assertEquals(5.0, ((Map<?, ?>) slot.get("enchantments")).get("sharpness"));
        assertNull(slot.get("potion"));
        assertFalse(b.toString().contains(" "));
    }

    @Test
    void jsonRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} x"));
        assertThrows(RuntimeException.class, () -> Json.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[".repeat(100)));
        assertEquals("tok", Json.str(Json.parse("{\"token\":\"tok\",\"wait\":5000}"), "token"));
        assertEquals(5000, Json.num(Json.parse("{\"token\":\"tok\",\"wait\":5000}"), "wait", 0));
    }

    @Test
    void sameMenuPageOnlyOnceAMinute() {
        Gate g = new Gate();
        Page a = new Page(Menus.Kind.AH, 0, "Auction (Page 1)", List.of(slot(0, "dirt", "$ 1")));
        Page b = new Page(Menus.Kind.AH, 0, "Auction (Page 2)", List.of(slot(0, "dirt", "$ 1")));
        assertTrue(g.admit(a, 1_000));
        assertFalse(g.admit(a, 2_000));
        assertTrue(g.admit(b, 2_000));
        assertTrue(g.admit(a, 1_000 + Gate.SAME_PAGE_MS));
    }

    @Test
    void quickBuyAtMostEvery30Seconds() {
        Gate g = new Gate();
        Page a = new Page(Menus.Kind.SHOP, 0, "Quick Buy", List.of(slot(0, "elytra", "$ 600M")));
        Page b = new Page(Menus.Kind.SHOP, 0, "Quick Buy", List.of(slot(0, "elytra", "$ 610M")));
        assertTrue(g.admit(a, 0));
        assertFalse(g.admit(b, 10_000));
        assertTrue(g.admit(b, Gate.SHOP_EVERY_MS));
    }

    @Test
    void readsOnlyOnceSlotsSettle() {
        Gate g = new Gate();
        Object screen = new Object();
        assertFalse(g.settled(screen, 1));
        assertFalse(g.settled(screen, 2)); // slots still arriving
        assertFalse(g.settled(screen, 2));
        assertTrue(g.settled(screen, 2));
        assertFalse(g.settled(screen, 2)); // read once, not every tick
        assertFalse(g.settled(screen, 3)); // next page
        assertFalse(g.settled(screen, 3));
        assertTrue(g.settled(screen, 3));
    }

    @Test
    void configIsOffUntilAsked(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Config c = new Config(dir);
        assertFalse(c.asked());
        assertFalse(c.enabled());
        c.answer(true);
        c.setToken("uuid1", "abc");
        Config again = new Config(dir);
        assertTrue(again.enabled());
        assertEquals("abc", again.tokenFor("uuid1"));
        Files.writeString(dir.resolve("config.json"), "{\"enabled\":true}");
        assertFalse(new Config(dir).enabled(), "enabled without having been asked stays off");
        Files.writeString(dir.resolve("config.json"), "not json");
        assertFalse(new Config(dir).enabled());
    }

    @Test
    void tokenBelongsToOneAccount(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Config c = new Config(dir);
        c.setToken("aaaa", "tok");
        assertEquals("tok", c.tokenFor("aaaa"));
        assertEquals("tok", c.tokenFor("AAAA"));
        assertNull(c.tokenFor("bbbb"), "another account never gets this token");
        assertNull(c.tokenFor(null));
        assertEquals("tok", new Config(dir).tokenFor("aaaa"), "kept across restarts with its owner");
        // An old plain-text token has no owner: logged in again rather than used.
        Files.writeString(dir.resolve("token"), "plain-old-token");
        assertNull(new Config(dir).tokenFor("aaaa"));
        c.setToken(null, null);
        assertFalse(Files.exists(dir.resolve("token")));
    }
}

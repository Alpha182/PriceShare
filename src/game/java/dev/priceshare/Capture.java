package dev.priceshare;

import dev.priceshare.core.Board;
import dev.priceshare.core.Menus;
import dev.priceshare.mixin.DialogScreenAccessor;
import dev.priceshare.core.Page;
import dev.priceshare.core.PriceShare;
import dev.priceshare.core.Slot;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs at the end of every client tick. Reads the open menu only if it is one of DonutSMP's
 * market menus and the player said yes. It never sends a packet, clicks, or opens anything.
 */
public final class Capture {
    private Capture() {}

    public static final Component PROMPT_TITLE = Component.literal("Share auction prices with DonutMarket?");
    public static final Component PROMPT_TEXT = Component.literal(
            "Only the items and prices in the Auction, Orders and Quick Buy menus, and the leaderboards you open, are sent, tied to your Minecraft name. You can turn this off any time.");

    public static void tick(Minecraft mc) {
        if (mc.player == null || !onDonut(mc)) {
            PriceShare.GATE.reset();
            return;
        }
        Screen screen = Screens.current(mc);
        if (screen == null && PriceShare.promptPending) {
            // Asked after the menu closes: opening a screen over it would close it on the server.
            PriceShare.promptPending = false;
            Screens.open(mc, new ConfirmScreen(yes -> {
                PriceShare.config().answer(yes);
                Screens.open(mc, null);
            }, PROMPT_TITLE, PROMPT_TEXT));
            return;
        }
        if (screen instanceof DialogScreen<?> ds) {
            dialog(ds);
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> cs) || !(cs.getMenu() instanceof ChestMenu chest)) {
            PriceShare.GATE.reset();
            return;
        }
        String title = Slot.plain(cs.getTitle().getString());
        Menus.Kind kind = Menus.classify(title);
        if (kind == null) {
            PriceShare.GATE.reset();
            return;
        }
        if (!PriceShare.config().asked()) {
            PriceShare.promptPending = true;
            return;
        }
        if (!PriceShare.config().enabled()) {
            return;
        }
        int n = chest.getRowCount() * 9;
        if (!PriceShare.GATE.settled(cs, signature(chest, n))) {
            return;
        }
        Page page = new Page(kind, System.currentTimeMillis(), title, read(chest, n));
        if (PriceShare.GATE.admit(page, page.at())) {
            PriceShare.share(page);
        }
    }

    /**
     * A server-sent dialog: a leaderboard if it lists more than 10 messages. Read once it has been
     * open 2 ticks; a dialog can't change while open (the server sends a new one), so the dialog
     * object itself is the content signature.
     */
    private static void dialog(DialogScreen<?> screen) {
        Dialog d = ((DialogScreenAccessor) screen).priceshare$dialog();
        if (d == null || Boards.messages(d) < Board.MIN_ROWS) {
            PriceShare.GATE.reset();
            return;
        }
        if (!PriceShare.config().asked()) {
            PriceShare.promptPending = true;
            return;
        }
        if (!PriceShare.config().enabled() || !PriceShare.GATE.settled(screen, System.identityHashCode(d))) {
            return;
        }
        Page page = Boards.read(d, System.currentTimeMillis());
        if (page != null && PriceShare.GATE.admit(page, page.at())) {
            PriceShare.share(page);
        }
    }

    private static boolean onDonut(Minecraft mc) {
        if (PriceShare.ANY_SERVER) return mc.getConnection() != null;
        ServerData server = mc.getCurrentServer();
        return server != null && Menus.isDonut(server.ip);
    }

    private static int signature(ChestMenu chest, int n) {
        int h = 1;
        for (int i = 0; i < n; i++) {
            ItemStack s = chest.getContainer().getItem(i);
            h = 31 * h + ItemStack.hashItemAndComponents(s) + s.getCount();
        }
        return h;
    }

    /** The chest part of the menu only: the player's own inventory below it is never read. */
    private static List<Slot> read(ChestMenu chest, int n) {
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack s = chest.getContainer().getItem(i);
            if (s.isEmpty()) continue;
            List<String> lore = new ArrayList<>();
            ItemLore l = s.get(DataComponents.LORE);
            if (l != null) for (Component line : l.lines()) lore.add(Slot.plain(line.getString()));
            Map<String, Integer> ench = new LinkedHashMap<>();
            add(ench, s.get(DataComponents.ENCHANTMENTS));
            add(ench, s.get(DataComponents.STORED_ENCHANTMENTS));
            String potion = null;
            PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
            if (pc != null && pc.potion().isPresent()) potion = Slot.path(pc.potion().get().getRegisteredName());
            out.add(new Slot(i, Slot.path(String.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()))), s.getCount(),
                    Slot.plain(s.getHoverName().getString()), lore, ench, potion));
        }
        return out;
    }

    private static void add(Map<String, Integer> out, ItemEnchantments e) {
        if (e == null) return;
        for (Object2IntMap.Entry<Holder<Enchantment>> x : e.entrySet()) {
            out.put(Slot.path(x.getKey().getRegisteredName()), x.getIntValue());
        }
    }
}

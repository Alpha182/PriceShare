package dev.priceshare;

import dev.priceshare.core.PriceShare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Price Share's settings, opened from Mod Menu. Only vanilla buttons, nothing drawn by hand. */
public final class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Price Share"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 155;
        int y = height / 4;
        addRenderableWidget(new StringWidget(0, y - 30, width, 9, Component.literal("Price Share: shares DonutSMP market menus with DonutMarket"), font));
        addRenderableWidget(new StringWidget(0, y - 16, width, 9, Component.literal(PriceShare.status()), font));
        addRenderableWidget(Button.builder(label("Sharing", PriceShare.config().enabled()), b -> {
            PriceShare.setEnabled(!PriceShare.config().enabled());
            rebuildWidgets();
        }).bounds(x, y, 310, 20).build());
        addRenderableWidget(Button.builder(label("Show my name on DonutMarket", PriceShare.config().showOnSite()), b -> {
            PriceShare.config().setShowOnSite(!PriceShare.config().showOnSite());
            rebuildWidgets();
        }).bounds(x, y + 24, 310, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y + 60, 310, 20).build());
    }

    private static Component label(String what, boolean on) {
        return Component.literal(what + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        Screens.open(Minecraft.getInstance(), parent);
    }
}

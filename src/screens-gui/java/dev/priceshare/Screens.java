package dev.priceshare;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Screens on 26.2 and later, where the open screen moved onto {@code Minecraft.gui}. */
public final class Screens {
    private Screens() {}

    public static Screen current(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void open(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }
}

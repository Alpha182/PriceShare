package dev.priceshare;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Screens on 1.21.11 and 26.1: {@code Minecraft.screen} and {@code Minecraft.setScreen}. */
public final class Screens {
    private Screens() {}

    public static Screen current(Minecraft mc) {
        return mc.screen;
    }

    public static void open(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }
}

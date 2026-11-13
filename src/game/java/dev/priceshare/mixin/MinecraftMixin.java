package dev.priceshare.mixin;

import dev.priceshare.Capture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mod's only hook into the game: a look at the open menu after each client tick. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void priceshare$tick(CallbackInfo ci) {
        try {
            Capture.tick((Minecraft) (Object) this);
        } catch (Throwable t) {
            // A format change in a new version must never crash the game.
            dev.priceshare.core.Log.warn("Skipped a menu: " + t);
        }
    }
}

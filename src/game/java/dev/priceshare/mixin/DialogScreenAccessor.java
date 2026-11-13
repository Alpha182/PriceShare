package dev.priceshare.mixin;

import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the dialog a dialog screen shows. Nothing is changed or clicked. */
@Mixin(DialogScreen.class)
public interface DialogScreenAccessor {
    @Accessor("dialog")
    Dialog priceshare$dialog();
}

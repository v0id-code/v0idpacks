package de.v0idpacks.fabric.mixin;

import de.v0idpacks.core.PackPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pack.class)
public abstract class PackTitleMixin {
    @Shadow public abstract String getId();
    @Unique private boolean v0idpacks$titleLoaded;
    @Unique private Component v0idpacks$title;

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true)
    private void v0idpacks$customTitle(CallbackInfoReturnable<Component> result) {
        if (!v0idpacks$titleLoaded) {
            v0idpacks$titleLoaded = true;
            try {
                String name = PackPresentation.readName(Minecraft.getInstance().getResourcePackDirectory(), getId());
                if (name != null) v0idpacks$title = Component.literal(name);
            } catch (java.io.IOException ignored) { /* Keep Minecraft's title for unreadable metadata. */ }
        }
        if (v0idpacks$title != null) result.setReturnValue(v0idpacks$title);
    }
}

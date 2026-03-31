package com.ui_utils.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ui_utils.SharedVariables;

@Mixin(InBedChatScreen.class)
public class SleepingChatScreenMixin extends Screen {
    protected SleepingChatScreenMixin(Component title) {
        super(title);
    }

    // called when InBedChatScreen is created
    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        // register "client wake up" button for InBedChatScreen if ui utils is enabled
        if (SharedVariables.enabled) {
            addRenderableWidget(Button.builder(Component.literal("Client wake up"), (button) -> {
                // wakes the player up client-side
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.stopSleeping();
                    this.minecraft.setScreen(null);
                }
            }).width(115).pos(5, 5).build());
        }
    }
}

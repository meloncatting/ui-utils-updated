package com.ui_utils.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.ui_utils.MainClient;
import com.ui_utils.SharedVariables;

import java.util.regex.Pattern;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
    private HandledScreenMixin() {
        super(null);
    }

    @Shadow
    protected abstract boolean checkHotbarKeyPressed(KeyEvent event);
    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput actionType);
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Unique
    private static final Minecraft mc = Minecraft.getInstance();

    @Unique
    private EditBox addressField;

    // called when creating a AbstractContainerScreen
    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        if (SharedVariables.enabled) {
            MainClient.createWidgets(mc, this);

            // create chat box
            this.addressField = new EditBox(this.font, 5, 245, 160, 20, Component.literal("Chat ...")) {
                @Override
                public boolean keyPressed(KeyEvent event) {
                    if (event.key() == GLFW.GLFW_KEY_ENTER) {
                        if (this.getValue().equals("^toggleuiutils")) {
                            SharedVariables.enabled = !SharedVariables.enabled;
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(Component.literal("UI-Utils is now " + (SharedVariables.enabled ? "enabled" : "disabled") + "."));
                            }
                            return false;
                        }

                        if (mc.getConnection() != null) {
                            if (this.getValue().startsWith("/")) {
                                mc.getConnection().sendCommand(this.getValue().replaceFirst(Pattern.quote("/"), ""));
                            } else {
                                mc.getConnection().sendChat(this.getValue());
                            }
                    } else {
                            MainClient.LOGGER.warn("Minecraft network handler (mc.getConnection()) was null while trying to send chat message from UI Utils.");
                        }

                        this.setValue("");
                    }
                    return super.keyPressed(event);
                }
            };
            this.addressField.setValue("");
            this.addressField.setMaxLength(256);

            this.addRenderableWidget(this.addressField);
        }
    }

    @Inject(at = @At("HEAD"), method = "keyPressed", cancellable = true)
    public void keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        cir.cancel();
        if (super.keyPressed(event)) {
            cir.setReturnValue(true);
        } else if (MainClient.mc.options.keyInventory.matches(event) && (this.addressField == null || !this.addressField.isActive())) {
            // Crashes if address field does not exist (because of ui utils disabled, this is a temporary fix.)
            this.onClose();
            cir.setReturnValue(true);
        } else {
            this.checkHotbarKeyPressed(event);
            if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
                if (mc.options.keyPickItem.matches(event)) {
                    this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.CLONE);
                } else if (mc.options.keyDrop.matches(event)) {
                    this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 ? 1 : 0, ContainerInput.THROW);
                }
            }

            cir.setReturnValue(true);
        }
    }

    // inject at the end of the extractRenderState method
    @Inject(at = @At("TAIL"), method = "extractRenderState")
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // display sync id, revision, if ui utils is enabled
        if (SharedVariables.enabled) {
            MainClient.createText(mc, context, this.font);
        }
    }
}

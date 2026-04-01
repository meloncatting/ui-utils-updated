package com.ui_utils.gui;

import com.ui_utils.UIUtilsConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** An EditBox that applies the UI Utils colour scheme when customStyle is enabled. */
public class UIUtilsEditBox extends EditBox {

    public UIUtilsEditBox(Font font, int x, int y, int w, int h, Component hint) {
        super(font, x, y, w, h, hint);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        UIUtilsConfig cfg = UIUtilsConfig.instance;
        if (cfg.customStyle) {
            int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
            context.fill(x, y, x + w, y + h, cfg.buttonColor);
            UIUtilsButton.drawBorder(context, x, y, w, h, cfg.borderColor, cfg.sharpCorners);
            this.setBordered(false);   // suppress vanilla border so text padding is minimal
            this.setTextColor(cfg.buttonTextColor);
        } else {
            this.setBordered(true);
            this.setTextColor(0xFFFFFFFF);
        }
        super.extractWidgetRenderState(context, mouseX, mouseY, delta);
    }
}

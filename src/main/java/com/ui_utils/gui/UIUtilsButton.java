package com.ui_utils.gui;

import com.ui_utils.UIUtilsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;

import net.minecraft.network.chat.Component;

/**
 * A Button subclass that supports custom background colours, borders,
 * and sharp/soft corner modes controlled by {@link UIUtilsConfig}.
 */
public class UIUtilsButton extends Button {

    private UIUtilsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    /** Draws a 1-px border using fill() calls only — no outline() ambiguity. */
    public static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color, boolean sharp) {
        if (sharp) {
            ctx.fill(x,         y,         x + w, y + 1,     color); // top
            ctx.fill(x,         y + h - 1, x + w, y + h,     color); // bottom
            ctx.fill(x,         y + 1,     x + 1, y + h - 1, color); // left
            ctx.fill(x + w - 1, y + 1,     x + w, y + h - 1, color); // right
        } else {
            // inset 1px at corners → softer look
            ctx.fill(x + 1,     y,         x + w - 1, y + 1,     color); // top
            ctx.fill(x + 1,     y + h - 1, x + w - 1, y + h,     color); // bottom
            ctx.fill(x,         y + 1,     x + 1,     y + h - 1, color); // left
            ctx.fill(x + w - 1, y + 1,     x + w,     y + h - 1, color); // right
        }
    }

    /** Factory — mirrors the ergonomics of Button.builder but without the builder boilerplate. */
    public static UIUtilsButton of(int x, int y, int width, int height, Component message, OnPress onPress) {
        return new UIUtilsButton(x, y, width, height, message, onPress);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        UIUtilsConfig cfg = UIUtilsConfig.instance;
        Font font = Minecraft.getInstance().font;
        int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();

        if (cfg.customStyle) {
            int bg = !this.active
                    ? 0xE0181818
                    : this.isHovered ? cfg.buttonHoverColor : cfg.buttonColor;

            context.fill(x, y, x + w, y + h, bg);

            drawBorder(context, x, y, w, h, cfg.borderColor, cfg.sharpCorners);

            int textColor = this.active ? cfg.buttonTextColor : 0xFFA0A0A0;
            context.centeredText(font, this.getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
        } else {
            // Vanilla style: extractContents is responsible for BOTH the sprite and the label.
            this.extractDefaultSprite(context);
            int textColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.centeredText(font, this.getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
        }
    }


}

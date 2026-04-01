package com.ui_utils.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * A Minecraft-native HSV colour picker widget.
 *
 * Layout (all coords relative to widget origin):
 *   ┌────────┬──────────────────────────────┐
 *   │ preview│  SV gradient panel           │
 *   │ (hue)  │                              │
 *   └────────┴──────────────────────────────┘
 *   ├────────── hue slider (rainbow) ───────┤
 *
 * - previewW  = PREVIEW_W px  (left strip, shows pure hue)
 * - svW       = width - PREVIEW_W - 2 px
 * - sliderH   = SLIDER_H px  (bottom strip)
 * - svH       = height - SLIDER_H - GAP px
 */
public class ColorPickerWidget extends AbstractWidget {

    private static final int PREVIEW_W = 72;
    private static final int SLIDER_H  = 14;
    private static final int GAP       = 3;

    private float hue = 0f;   // 0–360
    private float sat = 1f;   // 0–1
    private float val = 1f;   // 0–1
    private int   alpha = 0xFF;

    private boolean draggingSV  = false;
    private boolean draggingHue = false;

    private final IntConsumer onChange;  // called with the new ARGB colour on every change

    // ── constructor ──────────────────────────────────────────────────────────

    public ColorPickerWidget(int x, int y, int width, int height, IntConsumer onChange) {
        super(x, y, width, height, Component.empty());
        this.onChange = onChange;
    }

    // ── public API ───────────────────────────────────────────────────────────

    public void setColor(int argb) {
        alpha = (argb >> 24) & 0xFF;
        float[] hsv = rgbToHsv((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    public int getColor() {
        return hsvToArgb(hue, sat, val, alpha);
    }

    // ── rendering ────────────────────────────────────────────────────────────

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int x  = this.getX(), y  = this.getY();
        int tw = this.getWidth(), th = this.getHeight();

        int svH   = th - SLIDER_H - GAP;
        int svX   = x + PREVIEW_W + 2;
        int svW   = tw - PREVIEW_W - 2;
        int slY   = y + svH + GAP;

        // ── pure-hue colour (S=1 V=1) for the SV gradient ───────────────────
        int pureHue = hsvToArgb(hue, 1f, 1f, 0xFF);
        int phR = (pureHue >> 16) & 0xFF;
        int phG = (pureHue >> 8)  & 0xFF;
        int phB =  pureHue        & 0xFF;

        // ── SV gradient panel — one vertical strip per pixel ─────────────────
        for (int i = 0; i < svW; i++) {
            float s      = (float) i / (svW - 1);
            int   topR   = Math.round(255 * (1 - s) + phR * s);
            int   topG   = Math.round(255 * (1 - s) + phG * s);
            int   topB   = Math.round(255 * (1 - s) + phB * s);
            int   top    = 0xFF000000 | (topR << 16) | (topG << 8) | topB;
            ctx.fillGradient(svX + i, y, svX + i + 1, y + svH, top, 0xFF000000);
        }

        // ── preview strip — current selected colour ───────────────────────────
        ctx.fill(x, y, x + PREVIEW_W, y + svH, getColor() | 0xFF000000);

        // ── thin divider between preview and SV ───────────────────────────────
        ctx.fill(x + PREVIEW_W, y, svX, y + svH, 0xFF000000);

        // ── hue slider — 6 gradient segments covering 0–360° ─────────────────
        int[] hueColors = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00,
            0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        int segments = hueColors.length - 1;
        for (int i = 0; i < tw; i++) {
            float hpos = (float) i / tw * segments;
            int   seg  = (int) hpos;
            if (seg >= segments) seg = segments - 1;
            float t    = hpos - seg;
            int   c    = lerpColor(hueColors[seg], hueColors[seg + 1], t);
            ctx.fill(x + i, slY, x + i + 1, slY + SLIDER_H, c);
        }

        // ── SV handle ─────────────────────────────────────────────────────────
        int hx = svX + Math.round(sat * (svW - 1));
        int hy = y   + Math.round((1 - val) * (svH - 1));
        drawRing(ctx, hx, hy, 0xFFFFFFFF);

        // ── hue slider handle ─────────────────────────────────────────────────
        int hsx = x + Math.round(hue / 360f * (tw - 1));
        int hsy = slY + SLIDER_H / 2;
        drawRing(ctx, hsx, hsy, 0xFFFFFFFF);
        // outer ring in dark for contrast on light hue regions
        drawRing(ctx, hsx, hsy, 0x80000000);
    }

    // ── mouse interaction ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isPrimary) {
        if (!this.active || !this.visible || !isPrimary) return false;
        double mx = event.x(), my = event.y();
        int x  = this.getX(), y  = this.getY();
        int tw = this.getWidth(), th = this.getHeight();
        int svH = th - SLIDER_H - GAP;
        int svX = x + PREVIEW_W + 2;
        int svW = tw - PREVIEW_W - 2;
        int slY = y + svH + GAP;

        if (mx >= svX && mx < svX + svW && my >= y && my < y + svH) {
            draggingSV = true;
            updateSV(mx, my, svX, y, svW, svH);
            return true;
        }
        if (mx >= x && mx < x + tw && my >= slY && my < slY + SLIDER_H) {
            draggingHue = true;
            updateHue(mx, x, tw);
            return true;
        }
        return false;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        int x  = this.getX(), y  = this.getY();
        int tw = this.getWidth(), th = this.getHeight();
        int svH = th - SLIDER_H - GAP;
        int svX = x + PREVIEW_W + 2;
        int svW = tw - PREVIEW_W - 2;

        if (draggingSV)  updateSV(event.x(),  event.y(), svX, y, svW, svH);
        if (draggingHue) updateHue(event.x(), x, tw);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        draggingSV = draggingHue = false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    // ── helpers ───────────────────────────────────────────────────────────────

    private void updateSV(double mx, double my, int svX, int svY, int svW, int svH) {
        sat = (float) Math.clamp((mx - svX) / (svW - 1), 0.0, 1.0);
        val = 1f - (float) Math.clamp((my - svY) / (svH - 1), 0.0, 1.0);
        notifyChange();
    }

    private void updateHue(double mx, int startX, int totalW) {
        hue = (float) Math.clamp((mx - startX) / (totalW - 1), 0.0, 1.0) * 360f;
        notifyChange();
    }

    private void notifyChange() {
        if (onChange != null) onChange.accept(getColor());
    }

    /** Draw a 7×7 ring (approximately circular) centred at (cx, cy). */
    private static void drawRing(GuiGraphicsExtractor ctx, int cx, int cy, int color) {
        ctx.fill(cx - 2, cy - 3, cx + 3, cy - 2, color); // top bar
        ctx.fill(cx - 2, cy + 2, cx + 3, cy + 3, color); // bottom bar
        ctx.fill(cx - 3, cy - 2, cx - 2, cy + 2, color); // left bar
        ctx.fill(cx + 2, cy - 2, cx + 3, cy + 2, color); // right bar
    }

    // ── colour math ──────────────────────────────────────────────────────────

    public static int hsvToArgb(float h, float s, float v, int a) {
        float c  = v * s;
        float hh = ((h % 360f) + 360f) % 360f / 60f;
        float x  = c * (1f - Math.abs(hh % 2f - 1f));
        float m  = v - c;
        float r, g, b;
        int   i  = (int) hh;
        switch (i) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (a << 24)
             | (Math.round((r + m) * 255) << 16)
             | (Math.round((g + m) * 255) << 8)
             |  Math.round((b + m) * 255);
    }

    public static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float s = (max == 0f) ? 0f : delta / max;
        float v = max;
        float h = 0f;
        if (delta > 0f) {
            if (max == rf)      h = 60f * (((gf - bf) / delta) % 6f);
            else if (max == gf) h = 60f * ((bf - rf) / delta + 2f);
            else                h = 60f * ((rf - gf) / delta + 4f);
        }
        if (h < 0f) h += 360f;
        return new float[]{h, s, v};
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }
}

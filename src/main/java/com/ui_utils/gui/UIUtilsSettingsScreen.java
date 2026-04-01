package com.ui_utils.gui;

import com.ui_utils.UIUtilsConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UIUtilsSettingsScreen extends Screen {

    private final Screen parent;

    // which colour slot is being edited: 0=BG, 1=Hover, 2=Text, 3=Border
    private int activeSlot = 0;
    private static final String[] SLOT_NAMES = {"Background", "Hover", "Text", "Border"};

    private ColorPickerWidget picker;
    private EditBox hexField;
    private Button[] slotButtons = new Button[4];

    private boolean updatingFromPicker = false;
    private boolean updatingFromHex    = false;

    public UIUtilsSettingsScreen(Screen parent) {
        super(Component.literal("UI Utils Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int cx   = this.width  / 2;
        int picW = 250;
        int picH = 100;
        int picX = cx - picW / 2;
        int topY = 30;
        int sp   = 24;
        int row  = 0;

        // ── toggle row ───────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(styleLabel(), btn -> {
            UIUtilsConfig.instance.customStyle = !UIUtilsConfig.instance.customStyle;
            btn.setMessage(styleLabel());
        }).width(118).pos(cx - 122, topY + sp * row).build());

        this.addRenderableWidget(Button.builder(cornersLabel(), btn -> {
            UIUtilsConfig.instance.sharpCorners = !UIUtilsConfig.instance.sharpCorners;
            btn.setMessage(cornersLabel());
        }).width(118).pos(cx + 4, topY + sp * row).build());
        row++;

        // ── colour slot tabs ─────────────────────────────────────────────────
        int tabW = (picW - 3 * 4) / 4;  // 4 tabs, 4px gap between
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            slotButtons[i] = this.addRenderableWidget(Button.builder(
                Component.literal(SLOT_NAMES[i]), btn -> selectSlot(slot)
            ).width(tabW).pos(picX + (tabW + 4) * i, topY + sp * row).build());
        }
        row++;

        // ── colour picker ────────────────────────────────────────────────────
        picker = new ColorPickerWidget(picX, topY + sp * row, picW, picH, this::onPickerChanged);
        picker.setColor(getSlotColor(activeSlot));
        this.addRenderableWidget(picker);

        // ── hex field (below picker, 4px gap) ────────────────────────────────
        int hexY = topY + sp * row + picH + 4;
        hexField = new EditBox(this.font, picX, hexY, picW - 60, 20, Component.empty());
        hexField.setMaxLength(8);
        hexField.setValue(toHex(getSlotColor(activeSlot)));
        hexField.setResponder(this::onHexChanged);
        this.addRenderableWidget(hexField);

        // small coloured swatch next to hex field
        // (rendered in extractRenderState — no widget needed)

        // ── action buttons ────────────────────────────────────────────────────
        int btnY = hexY + 26;
        int btnW = 78;
        int gap  = 5;
        int totalBtnW = btnW * 3 + gap * 2;
        int btnX = cx - totalBtnW / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Apply"), btn -> applyColors())
                .width(btnW).pos(btnX, btnY).build());
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), btn -> {
            applyColors();
            UIUtilsConfig.save();
            this.minecraft.setScreen(this.parent);
        }).width(btnW).pos(btnX + btnW + gap, btnY).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn ->
                this.minecraft.setScreen(this.parent)
        ).width(btnW).pos(btnX + (btnW + gap) * 2, btnY).build());

        updateTabHighlights();
    }

    // ── slot selection ────────────────────────────────────────────────────────

    private void selectSlot(int slot) {
        activeSlot = slot;
        picker.setColor(getSlotColor(slot));
        updatingFromPicker = true;
        hexField.setValue(toHex(getSlotColor(slot)));
        updatingFromPicker = false;
        updateTabHighlights();
    }

    private void updateTabHighlights() {
        for (int i = 0; i < 4; i++) {
            // visually mark active tab by appending * — simple but clear
            slotButtons[i].setMessage(Component.literal(
                i == activeSlot ? "[ " + SLOT_NAMES[i] + " ]" : SLOT_NAMES[i]
            ));
        }
    }

    // ── change handlers ───────────────────────────────────────────────────────

    private void onPickerChanged(int argb) {
        if (updatingFromHex) return;
        setSlotColor(activeSlot, argb);
        updatingFromPicker = true;
        hexField.setValue(toHex(argb));
        updatingFromPicker = false;
    }

    private void onHexChanged(String hex) {
        if (updatingFromPicker || hex.length() != 8) return;
        try {
            int color = (int) Long.parseLong(hex, 16);
            updatingFromHex = true;
            setSlotColor(activeSlot, color);
            picker.setColor(color);
            updatingFromHex = false;
        } catch (NumberFormatException ignored) {}
    }

    private void applyColors() {
        // colours are already written to config in real time; just a no-op here
        // (kept for clarity / future use)
    }

    // ── slot helpers ──────────────────────────────────────────────────────────

    private int getSlotColor(int slot) {
        return switch (slot) {
            case 0 -> UIUtilsConfig.instance.buttonColor;
            case 1 -> UIUtilsConfig.instance.buttonHoverColor;
            case 2 -> UIUtilsConfig.instance.buttonTextColor;
            default -> UIUtilsConfig.instance.borderColor;
        };
    }

    private void setSlotColor(int slot, int color) {
        switch (slot) {
            case 0 -> UIUtilsConfig.instance.buttonColor      = color;
            case 1 -> UIUtilsConfig.instance.buttonHoverColor = color;
            case 2 -> UIUtilsConfig.instance.buttonTextColor  = color;
            default -> UIUtilsConfig.instance.borderColor     = color;
        }
    }

    // ── utilities ─────────────────────────────────────────────────────────────

    private static String toHex(int color) {
        return String.format("%08X", color & 0xFFFFFFFFL);
    }

    private static Component styleLabel() {
        return Component.literal("Custom Style: " + (UIUtilsConfig.instance.customStyle ? "ON" : "OFF"));
    }

    private static Component cornersLabel() {
        return Component.literal("Sharp Corners: " + (UIUtilsConfig.instance.sharpCorners ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

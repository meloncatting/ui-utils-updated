package com.ui_utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class UIUtilsConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ui_utils.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int buttonColor      = 0xE0202035;  // ARGB: dark blue-grey
    public int buttonHoverColor = 0xE0303050;  // ARGB: slightly lighter on hover
    public int buttonTextColor  = 0xFFFFFFFF;  // ARGB: white text
    public int borderColor      = 0xFF4060C0;  // ARGB: blue border

    public boolean customStyle  = false;  // when false, use vanilla Minecraft button style
    public boolean sharpCorners = false;  // when true, square border; when false, no border

    public static UIUtilsConfig instance = new UIUtilsConfig();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                UIUtilsConfig loaded = GSON.fromJson(r, UIUtilsConfig.class);
                if (loaded != null) instance = loaded;
            } catch (IOException e) {
                MainClient.LOGGER.error("Failed to load UI Utils config", e);
            }
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            MainClient.LOGGER.error("Failed to save UI Utils config", e);
        }
    }
}

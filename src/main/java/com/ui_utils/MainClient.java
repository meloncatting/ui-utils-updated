package com.ui_utils;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import com.ui_utils.gui.UIUtilsButton;
import com.ui_utils.gui.UIUtilsSettingsScreen;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ui_utils.mixin.accessor.ClientConnectionAccessor;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class MainClient implements ClientModInitializer {
    public static java.awt.Font monospace;
    public static Color darkWhite;

    public static KeyMapping restoreScreenKey;

    public static Logger LOGGER = LoggerFactory.getLogger("ui-utils");
    public static Minecraft mc = Minecraft.getInstance();
    @Override
    public void onInitializeClient() {
        UIUtilsConfig.load();
        UpdateUtils.checkForUpdates();

        // register "restore screen" key
        restoreScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("Restore Screen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyMapping.Category.MISC));

        // register event for END_CLIENT_TICK
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            // detect if the "restore screen" keybinding is pressed
            while (restoreScreenKey.consumeClick()) {
                if (SharedVariables.storedScreen != null && SharedVariables.storedScreenHandler != null && client.player != null) {
                    client.setScreen(SharedVariables.storedScreen);
                    client.player.containerMenu = SharedVariables.storedScreenHandler;
                }
            }
        });

        // set java.awt.headless to false if os is not mac (allows for JFrame guis to be used)
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            System.setProperty("java.awt.headless", "false");
            monospace = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10);
            darkWhite = new Color(220, 220, 220);
        }
    }

    @SuppressWarnings("all")
    public static void createText(Minecraft mc, GuiGraphicsExtractor context, net.minecraft.client.gui.Font font) {
        // display the current gui's sync id, revision
        context.text(font, "Sync Id: " + mc.player.containerMenu.containerId, 200, 5, Color.WHITE.getRGB());
        context.text(font, "Revision: " + mc.player.containerMenu.getStateId(), 200, 16, Color.WHITE.getRGB());
    }

    public static void createWidgets(Minecraft mc, Screen screen) {
        // Button layout: width=160, height=20, spacing=22px (2px gap), starting at y=15
        final int BW = 160, BX = 5, BY = 15, SP = 22;

        screen.addRenderableWidget(UIUtilsButton.of(BX, BY, BW, 20, Component.literal("Close without packet"), (button) -> {
            mc.setScreen(null);
        }));

        screen.addRenderableWidget(UIUtilsButton.of(BX, BY + SP, BW, 20, Component.literal("De-sync"), (button) -> {
            if (mc.getConnection() != null && mc.player != null) {
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            } else {
                LOGGER.warn("Minecraft network handler or player was null while using 'De-sync' in UI Utils.");
            }
        }));

        screen.addRenderableWidget(UIUtilsButton.of(BX, BY + SP * 2, BW, 20, Component.literal("Send packets: " + SharedVariables.sendUIPackets), (button) -> {
            SharedVariables.sendUIPackets = !SharedVariables.sendUIPackets;
            button.setMessage(Component.literal("Send packets: " + SharedVariables.sendUIPackets));
        }));

        screen.addRenderableWidget(UIUtilsButton.of(BX, BY + SP * 3, BW, 20, Component.literal("Delay packets: " + SharedVariables.delayUIPackets), (button) -> {
            SharedVariables.delayUIPackets = !SharedVariables.delayUIPackets;
            button.setMessage(Component.literal("Delay packets: " + SharedVariables.delayUIPackets));
            if (!SharedVariables.delayUIPackets && !SharedVariables.delayedUIPackets.isEmpty() && mc.getConnection() != null) {
                for (Packet<?> packet : SharedVariables.delayedUIPackets) {
                    mc.getConnection().send(packet);
                }
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("Sent " + SharedVariables.delayedUIPackets.size() + " packets."));
                }
                SharedVariables.delayedUIPackets.clear();
            }
        }));

        screen.addRenderableWidget(UIUtilsButton.of(BX, BY + SP * 4, BW, 20, Component.literal("Save GUI"), (button) -> {
            if (mc.player != null) {
                SharedVariables.storedScreen = mc.screen;
                SharedVariables.storedScreenHandler = mc.player.containerMenu;
            }
        }));

        screen.addRenderableWidget(UIUtilsButton.of(BX, BY + SP * 5, BW, 20, Component.literal("Disconnect and send packets"), (button) -> {
            SharedVariables.delayUIPackets = false;
            if (mc.getConnection() != null) {
                for (Packet<?> packet : SharedVariables.delayedUIPackets) {
                    mc.getConnection().send(packet);
                }
                mc.getConnection().getConnection().disconnect(Component.literal("Disconnecting (UI-UTILS)"));
            } else {
                LOGGER.warn("Minecraft network handler (mc.getConnection()) is null while client is disconnecting.");
            }
            SharedVariables.delayedUIPackets.clear();
        }));

        UIUtilsButton fabricatePacketButton = UIUtilsButton.of(BX, BY + SP * 6, BW, 20, Component.literal("Fabricate packet"), (button) -> {
            // creates a gui allowing you to fabricate packets

            JFrame frame = new JFrame("Choose Packet");
            frame.setBounds(0, 0, 450, 100);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setLayout(null);

            JButton clickSlotButton = getPacketOptionButton("Click Slot");
            clickSlotButton.setBounds(100, 25, 110, 20);
            clickSlotButton.addActionListener((event) -> {
                // im too lazy to comment everything here just read the code yourself
                frame.setVisible(false);

                JFrame clickSlotFrame = new JFrame("Click Slot Packet");
                clickSlotFrame.setBounds(0, 0, 450, 300);
                clickSlotFrame.setResizable(false);
                clickSlotFrame.setLocationRelativeTo(null);
                clickSlotFrame.setLayout(null);

                JLabel syncIdLabel = new JLabel("Sync Id:");
                syncIdLabel.setFocusable(false);
                syncIdLabel.setFont(monospace);
                syncIdLabel.setBounds(25, 25, 100, 20);

                JLabel revisionLabel = new JLabel("Revision:");
                revisionLabel.setFocusable(false);
                revisionLabel.setFont(monospace);
                revisionLabel.setBounds(25, 50, 100, 20);

                JLabel slotLabel = new JLabel("Slot:");
                slotLabel.setFocusable(false);
                slotLabel.setFont(monospace);
                slotLabel.setBounds(25, 75, 100, 20);

                JLabel buttonLabel = new JLabel("Button:");
                buttonLabel.setFocusable(false);
                buttonLabel.setFont(monospace);
                buttonLabel.setBounds(25, 100, 100, 20);

                JLabel actionLabel = new JLabel("Action:");
                actionLabel.setFocusable(false);
                actionLabel.setFont(monospace);
                actionLabel.setBounds(25, 125, 100, 20);

                JLabel timesToSendLabel = new JLabel("Times to send:");
                timesToSendLabel.setFocusable(false);
                timesToSendLabel.setFont(monospace);
                timesToSendLabel.setBounds(25, 190, 100, 20);

                JTextField syncIdField = new JTextField(1);
                syncIdField.setFont(monospace);
                syncIdField.setBounds(125, 25, 100, 20);

                JTextField revisionField = new JTextField(1);
                revisionField.setFont(monospace);
                revisionField.setBounds(125, 50, 100, 20);

                JTextField slotField = new JTextField(1);
                slotField.setFont(monospace);
                slotField.setBounds(125, 75, 100, 20);

                JTextField buttonField = new JTextField(1);
                buttonField.setFont(monospace);
                buttonField.setBounds(125, 100, 100, 20);

                JComboBox<String> actionField = new JComboBox<>(new Vector<>(ImmutableList.of(
                        "PICKUP",
                        "QUICK_MOVE",
                        "SWAP",
                        "CLONE",
                        "THROW",
                        "QUICK_CRAFT",
                        "PICKUP_ALL"
                )));
                actionField.setFocusable(false);
                actionField.setEditable(false);
                actionField.setBorder(BorderFactory.createEmptyBorder());
                actionField.setBackground(darkWhite);
                actionField.setFont(monospace);
                actionField.setBounds(125, 125, 100, 20);

                JLabel statusLabel = new JLabel();
                statusLabel.setVisible(false);
                statusLabel.setFocusable(false);
                statusLabel.setFont(monospace);
                statusLabel.setBounds(210, 150, 190, 20);

                JCheckBox delayBox = new JCheckBox("Delay");
                delayBox.setBounds(115, 150, 85, 20);
                delayBox.setSelected(false);
                delayBox.setFont(monospace);
                delayBox.setFocusable(false);

                JTextField timesToSendField = new JTextField("1");
                timesToSendField.setFont(monospace);
                timesToSendField.setBounds(125, 190, 100, 20);

                JButton sendButton = new JButton("Send");
                sendButton.setFocusable(false);
                sendButton.setBounds(25, 150, 75, 20);
                sendButton.setBorder(BorderFactory.createEtchedBorder());
                sendButton.setBackground(darkWhite);
                sendButton.setFont(monospace);
                sendButton.addActionListener((event0) -> {
                    if (
                            MainClient.isInteger(syncIdField.getText()) &&
                                    MainClient.isInteger(revisionField.getText()) &&
                                    MainClient.isInteger(slotField.getText()) &&
                                    MainClient.isInteger(buttonField.getText()) &&
                                    MainClient.isInteger(timesToSendField.getText()) &&
                                    actionField.getSelectedItem() != null) {
                        int syncId = Integer.parseInt(syncIdField.getText());
                        int revision = Integer.parseInt(revisionField.getText());
                        short slot = Short.parseShort(slotField.getText());
                        byte button0 = Byte.parseByte(buttonField.getText());
                        ContainerInput action = MainClient.stringToContainerInput(actionField.getSelectedItem().toString());
                        int timesToSend = Integer.parseInt(timesToSendField.getText());

                        if (action != null) {
                            ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(syncId, revision, slot, button0, action, new Int2ObjectArrayMap<>(), HashedStack.EMPTY);
                            try {
                                Runnable toRun = getFabricatePacketRunnable(mc, delayBox.isSelected(), packet);
                                for (int i = 0; i < timesToSend; i++) {
                                    toRun.run();
                                }
                            } catch (Exception e) {
                                statusLabel.setForeground(Color.RED.darker());
                                statusLabel.setText("You must be connected to a server!");
                                MainClient.queueTask(() -> {
                                    statusLabel.setVisible(false);
                                    statusLabel.setText("");
                                }, 1500L);
                                return;
                            }
                            statusLabel.setVisible(true);
                            statusLabel.setForeground(Color.GREEN.darker());
                            statusLabel.setText("Sent successfully!");
                            MainClient.queueTask(() -> {
                                statusLabel.setVisible(false);
                                statusLabel.setText("");
                            }, 1500L);
                        } else {
                            statusLabel.setVisible(true);
                            statusLabel.setForeground(Color.RED.darker());
                            statusLabel.setText("Invalid arguments!");
                            MainClient.queueTask(() -> {
                                statusLabel.setVisible(false);
                                statusLabel.setText("");
                            }, 1500L);
                        }
                    } else {
                        statusLabel.setVisible(true);
                        statusLabel.setForeground(Color.RED.darker());
                        statusLabel.setText("Invalid arguments!");
                        MainClient.queueTask(() -> {
                            statusLabel.setVisible(false);
                            statusLabel.setText("");
                        }, 1500L);
                    }
                });

                clickSlotFrame.add(syncIdLabel);
                clickSlotFrame.add(revisionLabel);
                clickSlotFrame.add(slotLabel);
                clickSlotFrame.add(buttonLabel);
                clickSlotFrame.add(actionLabel);
                clickSlotFrame.add(timesToSendLabel);
                clickSlotFrame.add(syncIdField);
                clickSlotFrame.add(revisionField);
                clickSlotFrame.add(slotField);
                clickSlotFrame.add(buttonField);
                clickSlotFrame.add(actionField);
                clickSlotFrame.add(sendButton);
                clickSlotFrame.add(statusLabel);
                clickSlotFrame.add(delayBox);
                clickSlotFrame.add(timesToSendField);
                clickSlotFrame.setVisible(true);
            });

            JButton buttonClickButton = getPacketOptionButton("Button Click");
            buttonClickButton.setBounds(250, 25, 110, 20);
            buttonClickButton.addActionListener((event) -> {
                frame.setVisible(false);

                JFrame buttonClickFrame = new JFrame("Button Click Packet");
                buttonClickFrame.setBounds(0, 0, 450, 250);
                buttonClickFrame.setResizable(false);
                buttonClickFrame.setLocationRelativeTo(null);
                buttonClickFrame.setLayout(null);

                JLabel syncIdLabel = new JLabel("Sync Id:");
                syncIdLabel.setFocusable(false);
                syncIdLabel.setFont(monospace);
                syncIdLabel.setBounds(25, 25, 100, 20);

                JLabel buttonIdLabel = new JLabel("Button Id:");
                buttonIdLabel.setFocusable(false);
                buttonIdLabel.setFont(monospace);
                buttonIdLabel.setBounds(25, 50, 100, 20);

                JTextField syncIdField = new JTextField(1);
                syncIdField.setFont(monospace);
                syncIdField.setBounds(125, 25, 100, 20);

                JTextField buttonIdField = new JTextField(1);
                buttonIdField.setFont(monospace);
                buttonIdField.setBounds(125, 50, 100, 20);

                JLabel statusLabel = new JLabel();
                statusLabel.setVisible(false);
                statusLabel.setFocusable(false);
                statusLabel.setFont(monospace);
                statusLabel.setBounds(210, 95, 190, 20);

                JCheckBox delayBox = new JCheckBox("Delay");
                delayBox.setBounds(115, 95, 85, 20);
                delayBox.setSelected(false);
                delayBox.setFont(monospace);
                delayBox.setFocusable(false);

                JLabel timesToSendLabel = new JLabel("Times to send:");
                timesToSendLabel.setFocusable(false);
                timesToSendLabel.setFont(monospace);
                timesToSendLabel.setBounds(25, 130, 100, 20);

                JTextField timesToSendField = new JTextField("1");
                timesToSendField.setFont(monospace);
                timesToSendField.setBounds(125, 130, 100, 20);

                JButton sendButton = new JButton("Send");
                sendButton.setFocusable(false);
                sendButton.setBounds(25, 95, 75, 20);
                sendButton.setBorder(BorderFactory.createEtchedBorder());
                sendButton.setBackground(darkWhite);
                sendButton.setFont(monospace);
                sendButton.addActionListener((event0) -> {
                    if (
                            MainClient.isInteger(syncIdField.getText()) &&
                            MainClient.isInteger(buttonIdField.getText()) &&
                            MainClient.isInteger(timesToSendField.getText())) {
                        int syncId = Integer.parseInt(syncIdField.getText());
                        int buttonId = Integer.parseInt(buttonIdField.getText());
                        int timesToSend = Integer.parseInt(timesToSendField.getText());

                        ServerboundContainerButtonClickPacket packet = new ServerboundContainerButtonClickPacket(syncId, buttonId);
                        try {
                            Runnable toRun = getFabricatePacketRunnable(mc, delayBox.isSelected(), packet);
                            for (int i = 0; i < timesToSend; i++) {
                                toRun.run();
                            }
                        } catch (Exception e) {
                            statusLabel.setVisible(true);
                            statusLabel.setForeground(Color.RED.darker());
                            statusLabel.setText("You must be connected to a server!");
                            MainClient.queueTask(() -> {
                                statusLabel.setVisible(false);
                                statusLabel.setText("");
                            }, 1500L);
                            return;
                        }
                        statusLabel.setVisible(true);
                        statusLabel.setForeground(Color.GREEN.darker());
                        statusLabel.setText("Sent successfully!");
                        MainClient.queueTask(() -> {
                            statusLabel.setVisible(false);
                            statusLabel.setText("");
                        }, 1500L);
                    } else {
                        statusLabel.setVisible(true);
                        statusLabel.setForeground(Color.RED.darker());
                        statusLabel.setText("Invalid arguments!");
                        MainClient.queueTask(() -> {
                            statusLabel.setVisible(false);
                            statusLabel.setText("");
                        }, 1500L);
                    }
                });

                buttonClickFrame.add(syncIdLabel);
                buttonClickFrame.add(buttonIdLabel);
                buttonClickFrame.add(syncIdField);
                buttonClickFrame.add(timesToSendLabel);
                buttonClickFrame.add(buttonIdField);
                buttonClickFrame.add(sendButton);
                buttonClickFrame.add(statusLabel);
                buttonClickFrame.add(delayBox);
                buttonClickFrame.add(timesToSendField);
                buttonClickFrame.setVisible(true);
            });

            frame.add(clickSlotButton);
            frame.add(buttonClickButton);
            frame.setVisible(true);
        });
        fabricatePacketButton.active = !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        screen.addRenderableWidget(fabricatePacketButton);

        screen.addRenderableWidget(UIUtilsButton.of(5, 15 + 22 * 7, 160, 20, Component.literal("Copy GUI Title JSON"), (button) -> {
            try {
                if (mc.screen == null) {
                    throw new IllegalStateException("The current minecraft screen (mc.screen) is null");
                }
                mc.keyboardHandler.setClipboard(new Gson().toJson(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, mc.screen.getTitle()).getOrThrow()));
            } catch (IllegalStateException e) {
                LOGGER.error("Error while copying title JSON to clipboard", e);
            }
        }));

        // Settings button — opens UIUtilsSettingsScreen
        screen.addRenderableWidget(UIUtilsButton.of(5, 15 + 22 * 8, 160, 20, Component.literal("Settings"), (button) -> {
            mc.setScreen(new UIUtilsSettingsScreen(mc.screen));
        }));
    }

    @NotNull
    private static JButton getPacketOptionButton(String label) {
        JButton button = new JButton(label);
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEtchedBorder());
        button.setBackground(darkWhite);
        button.setFont(monospace);
        return button;
    }

    @NotNull
    private static Runnable getFabricatePacketRunnable(Minecraft mc, boolean delay, Packet<?> packet) {
        Runnable toRun;
        if (delay) {
            toRun = () -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(packet);
                } else {
                    LOGGER.warn("Minecraft network handler (mc.getConnection()) is null while sending fabricated packets.");
                }
            };
        } else {
            toRun = () -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(packet);
                } else {
                    LOGGER.warn("Minecraft network handler (mc.getConnection()) is null while sending fabricated packets.");
                }
                ((ClientConnectionAccessor) mc.getConnection().getConnection()).getChannel().writeAndFlush(packet);
            };
        }
        return toRun;
    }

    public static boolean isInteger(String string) {
        try {
            Integer.parseInt(string);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static ContainerInput stringToContainerInput(String string) {
        // converts a string to ContainerInput
        return switch (string) {
            case "PICKUP" -> ContainerInput.PICKUP;
            case "QUICK_MOVE" -> ContainerInput.QUICK_MOVE;
            case "SWAP" -> ContainerInput.SWAP;
            case "CLONE" -> ContainerInput.CLONE;
            case "THROW" -> ContainerInput.THROW;
            case "QUICK_CRAFT" -> ContainerInput.QUICK_CRAFT;
            case "PICKUP_ALL" -> ContainerInput.PICKUP_ALL;
            default -> null;
        };
    }

    public static void queueTask(Runnable runnable, long delayMs) {
        // queues a task for minecraft to run
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Minecraft.getInstance().execute(runnable);
            }
        };
        timer.schedule(task, delayMs);
    }

    public static String getModVersion(String modId) {
        ModMetadata modMetadata = FabricLoader.getInstance().getModContainer(modId).isPresent() ? FabricLoader.getInstance().getModContainer(modId).get().getMetadata() : null;

        return modMetadata != null ? modMetadata.getVersion().getFriendlyString() : "null";
    }
}

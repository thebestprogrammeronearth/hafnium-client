/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.chat;

import dev.hafnium.client.gui.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Coloured module chat lines: "[Hafnium] Module : body". Same colours and builder as Anubis. */
public final class ModuleChat {
    private static final int DARK_GREY = 0x555555;
    private static final int GREY = 0xAAAAAA;
    private static final int WHITE = 0xFFFFFF;
    private static final int DANGER = 0xFF5555;
    private static final int GOOD = 0x55FF55;
    private static final int WARN = 0xFFAA00;
    private static final int VALUE = 0x55FFFF;

    private ModuleChat() {
    }

    public static Body body() {
        return new Body();
    }

    public static void send(String module, Body body) {
        Component built = body.build();
        onClientThread(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.getChat().addMessage(line(module, built));
            }
        });
    }

    public static void actionBar(String module, Body body) {
        Component built = body.build();
        onClientThread(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(line(module, built), false);
            }
        });
    }

    public static MutableComponent line(String name, Component body) {
        return Component.empty()
                .append(Component.literal("[").withColor(DARK_GREY))
                .append(Component.literal("Hafnium").withColor(ThemeManager.accent() & 0xFFFFFF))
                .append(Component.literal("] ").withColor(DARK_GREY))
                .append(Component.literal(name).withColor(WHITE))
                .append(Component.literal(" : ").withColor(DARK_GREY))
                .append(Component.empty().withColor(GREY).append(body));
    }

    private static void onClientThread(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }

    public static final class Body {
        private final MutableComponent root = Component.empty().withColor(GREY);

        private Body() {
        }

        public Body text(String text) {
            root.append(Component.literal(text));
            return this;
        }

        public Body name(String name) {
            return part(name, WHITE);
        }

        public Body danger(String text) {
            return part(text, DANGER);
        }

        public Body good(String text) {
            return part(text, GOOD);
        }

        public Body warn(String text) {
            return part(text, WARN);
        }

        public Body value(Object value) {
            return part(String.valueOf(value), VALUE);
        }

        public Body coords(int x, int y, int z) {
            return part(x + ", " + y + ", " + z, VALUE);
        }

        public Body append(Component component) {
            root.append(component);
            return this;
        }

        public Component build() {
            return root.copy();
        }

        private Body part(String text, int rgb) {
            root.append(Component.literal(text).withColor(rgb));
            return this;
        }
    }
}

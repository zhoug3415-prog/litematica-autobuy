package dev.autobuy.litematica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class Feedback {
    private static final Logger LOG = AutoBuyClient.LOGGER;

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<String, Long> throttles = new HashMap<>();
    private AutoBuyConfig config;
    private String lastChat = "";
    private long lastChatAt;

    public Feedback(AutoBuyConfig config) {
        this.config = config;
    }

    public void setConfig(AutoBuyConfig config) {
        this.config = config;
    }

    public void clearThrottles() {
        throttles.clear();
    }

    public void chat(String message) {
        sendChat(message, false);
    }

    public void chatThrottled(String key, String message, long intervalMs) {
        long now = System.currentTimeMillis();
        Long last = throttles.get(key);
        if (last != null && now - last < intervalMs) {
            return;
        }
        throttles.put(key, now);
        sendChat(message, false);
    }

    private void sendChat(String message, boolean force) {
        if (config == null || (!force && !config.chatFeedback)) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        String full = "[AutoBuy] " + message;
        long now = System.currentTimeMillis();
        if (!force && full.equals(lastChat) && now - lastChatAt < 5_000L) {
            return;
        }

        lastChat = full;
        lastChatAt = now;
        player.sendSystemMessage(Component.literal(full));
    }

    public void actionBar(String message) {
        if (config == null || !config.actionBarSummary) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        player.sendOverlayMessage(Component.literal("[AutoBuy] " + message));
    }

    public void debug(String message) {
        if (config != null && config.debugLog) {
            LOG.info("[AutoBuy] {}", message);
        }
    }

    public void error(String message, Throwable throwable) {
        if (throwable == null) {
            LOG.error("[AutoBuy] {}", message);
        } else {
            LOG.error("[AutoBuy] {}", message, throwable);
        }
        actionBar("錯誤：" + message);
        chat("錯誤：" + message);
    }
}

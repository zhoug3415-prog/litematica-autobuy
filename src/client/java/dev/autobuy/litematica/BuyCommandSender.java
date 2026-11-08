package dev.autobuy.litematica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class BuyCommandSender {
    private static final Logger LOG = AutoBuyClient.LOGGER;

    private final Deque<Order> queue = new ArrayDeque<>();
    private int cooldownMs;
    private int maxPerOrder;
    private int maxQueueSize;
    private boolean skipWhenScreenOpen;
    private long lastSentAtMs;
    private String lastError;

    public BuyCommandSender(AutoBuyConfig config) {
        updateConfig(config);
    }

    public void updateConfig(AutoBuyConfig config) {
        this.cooldownMs = config.commandCooldownMs;
        this.maxPerOrder = config.maxItemsPerOrder;
        this.maxQueueSize = Math.max(1, config.maxCommandsPerScan);
        this.skipWhenScreenOpen = config.skipWhenScreenOpen;
    }

    public boolean enqueue(String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return false;
        }
        if (queue.size() >= maxQueueSize) {
            return false;
        }
        queue.addLast(new Order(itemId, Math.min(count, maxPerOrder)));
        return true;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        lastError = null;
    }

    public long cooldownRemainingMs(long nowMs) {
        return Math.max(0L, cooldownMs - Math.max(0L, nowMs - lastSentAtMs));
    }

    public List<Order> tick(Minecraft mc, long nowMs) {
        if (queue.isEmpty()) {
            return List.of();
        }

        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) {
            queue.clear();
            return List.of();
        }
        if (player.isCreative() || player.isSpectator()) {
            queue.clear();
            return List.of();
        }
        if (skipWhenScreenOpen && mc.gui != null && mc.gui.screen() != null) {
            return List.of();
        }
        if (nowMs - lastSentAtMs < cooldownMs) {
            return List.of();
        }

        Order order = queue.pollFirst();
        if (order == null) {
            return List.of();
        }

        int count = Math.min(order.count(), maxPerOrder);
        if (count <= 0) {
            return List.of();
        }

        String command = "buy " + order.itemId() + " " + count;
        try {
            player.connection.sendCommand(command);
            lastSentAtMs = nowMs;
            lastError = null;
            LOG.info("[AutoBuy] Sent /{}", command);
            return List.of(new Order(order.itemId(), count));
        } catch (Exception | LinkageError e) {
            lastError = "送出 /buy 失敗：" + e.getMessage();
            LOG.error("[AutoBuy] Failed to send /{}", command, e);
            return List.of();
        }
    }

    public String consumeLastError() {
        String error = lastError;
        lastError = null;
        return error;
    }

    public record Order(String itemId, int count) {
    }
}

package dev.autobuy.litematica;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PendingOrderTracker {
    private final Map<String, PendingOrder> pending = new LinkedHashMap<>();
    private final Map<String, Integer> attemptHistory = new LinkedHashMap<>();

    public void record(String itemId, int count, int baselineCount, long nowMs) {
        int attempt = attemptHistory.getOrDefault(itemId, 0) + 1;
        pending.put(itemId, new PendingOrder(itemId, count, baselineCount, nowMs, attempt));
    }

    public boolean isPending(String itemId) {
        return pending.containsKey(itemId);
    }

    public int pendingCount() {
        return pending.size();
    }

    public Map<String, Integer> requestedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PendingOrder order : pending.values()) {
            counts.merge(order.itemId(), order.requestedCount(), (left, right) ->
                    (int) Math.min(Integer.MAX_VALUE, (long) left + right));
        }
        return counts;
    }

    public Optional<PendingOrder> mostRecent() {
        PendingOrder best = null;
        for (PendingOrder order : pending.values()) {
            if (best == null || order.sentAtMs() > best.sentAtMs()) {
                best = order;
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<PendingOrder> findMentioned(String lowerText) {
        PendingOrder best = null;
        for (PendingOrder order : pending.values()) {
            String id = order.itemId();
            String bare = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (lowerText.contains(id) || lowerText.contains(bare)) {
                if (best == null || order.sentAtMs() > best.sentAtMs()) {
                    best = order;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public PendingOrder removeForFailure(String itemId) {
        attemptHistory.remove(itemId);
        return pending.remove(itemId);
    }

    public List<Event> update(long nowMs, Map<String, Integer> inventoryCounts,
                              long timeoutMs, int maxRetries) {
        List<Event> events = new ArrayList<>();
        Iterator<Map.Entry<String, PendingOrder>> iterator = pending.entrySet().iterator();

        while (iterator.hasNext()) {
            PendingOrder order = iterator.next().getValue();
            int current = inventoryCounts.getOrDefault(order.itemId(), 0);

            if (current > order.baselineCount()) {
                iterator.remove();
                attemptHistory.remove(order.itemId());
                events.add(new Event(EventType.DELIVERED, order.itemId(), order.attempt(),
                        order.requestedCount()));
            } else if (nowMs - order.sentAtMs() >= timeoutMs) {
                iterator.remove();
                if (order.attempt() > maxRetries) {
                    events.add(new Event(EventType.RETRY_EXHAUSTED, order.itemId(), order.attempt(),
                            order.requestedCount()));
                } else {
                    attemptHistory.put(order.itemId(), order.attempt());
                    events.add(new Event(EventType.TIMEOUT_RETRY, order.itemId(), order.attempt(),
                            order.requestedCount()));
                }
            }
        }

        return events;
    }

    public void retainAttempts(Set<String> itemIds) {
        attemptHistory.keySet().removeIf(id -> !itemIds.contains(id));
    }

    public void clearAttemptHistory() {
        attemptHistory.clear();
    }

    public void clear() {
        pending.clear();
        attemptHistory.clear();
    }

    public record PendingOrder(String itemId, int requestedCount, int baselineCount,
                               long sentAtMs, int attempt) {
    }

    public enum EventType {
        DELIVERED,
        TIMEOUT_RETRY,
        RETRY_EXHAUSTED
    }

    public record Event(EventType type, String itemId, int attempt, int requestedCount) {
    }
}

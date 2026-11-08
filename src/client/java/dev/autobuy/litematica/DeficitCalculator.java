package dev.autobuy.litematica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class DeficitCalculator {
    public Result calculate(Map<String, Integer> required, Map<String, Integer> available, AutoBuyConfig config) {
        List<Deficit> deficits = new ArrayList<>();
        List<String> notWhitelisted = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int need = Math.max(0, entry.getValue());
            int have = Math.max(0, available.getOrDefault(entry.getKey(), 0));
            int missing = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) need - have));
            if (missing == 0) {
                continue;
            }

            boolean allowed = config.isWhitelisted(entry.getKey());
            deficits.add(new Deficit(entry.getKey(), need, have, missing, allowed));
            if (!allowed) {
                notWhitelisted.add(entry.getKey());
            }
        }

        deficits.sort(Comparator.comparingInt(Deficit::missing).reversed()
                .thenComparing(Deficit::itemId));
        notWhitelisted.sort(String::compareTo);
        return new Result(List.copyOf(deficits), List.copyOf(notWhitelisted));
    }

    public record Deficit(String itemId, int required, int available, int missing, boolean whitelisted) {
    }

    public record Result(List<Deficit> deficits, List<String> notWhitelisted) {
    }
}

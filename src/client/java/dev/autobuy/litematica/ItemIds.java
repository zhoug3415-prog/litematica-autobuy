package dev.autobuy.litematica;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ItemIds {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private ItemIds() {
    }

    public static String normalizeRegistryId(String id) {
        if (id == null) {
            return "";
        }
        String value = id.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith(MINECRAFT_PREFIX)) {
            value = value.substring(MINECRAFT_PREFIX.length());
        }
        return value;
    }

    public static String normalizeWhitelistEntry(String raw) {
        String value = normalizeRegistryId(raw);
        if (value.isEmpty() || value.startsWith("/")) {
            return null;
        }
        if (value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0) {
            return null;
        }
        return VALID.matcher(value).matches() ? value : null;
    }
}

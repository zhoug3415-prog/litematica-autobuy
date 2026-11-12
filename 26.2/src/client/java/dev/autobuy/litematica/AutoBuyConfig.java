package dev.autobuy.litematica;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AutoBuyConfig {
    public enum PrinterGateMode {
        ALWAYS,
        PRINTER_ENABLED,
        PRINTER_PRINTING
    }

    public enum RequiredCountMode {
        MISSING,
        TOTAL
    }

    public enum PurchaseMode {
        EXACT_LAYER,
        BULK
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "litematica-autobuy.json";

    public int scanIntervalTicks = 40;
    public PrinterGateMode printerGateMode = PrinterGateMode.PRINTER_ENABLED;
    public RequiredCountMode requiredCountMode = RequiredCountMode.MISSING;
    public boolean countOffhand = true;
    public List<String> whitelist = new ArrayList<>();
    public boolean whitelistEmptyAllowAll = false;
    public boolean autoSyncWhitelist = false;
    public boolean stockGateEnabled = true;
    public int stockHighStacks = 4;
    public int stockLowStacks = 1;
    public int stockTargetStacks = 4;
    public PurchaseMode purchaseMode = PurchaseMode.BULK;
    public int commandCooldownMs = 1000;
    public int maxCommandsPerScan = 4;
    public int maxItemsPerOrder = 576;
    public int pendingTimeoutSeconds = 30;
    public int pendingMaxRetries = 2;
    public boolean skipWhenScreenOpen = true;
    public boolean skipWhenInventoryFull = true;
    public boolean chatFeedback = true;
    public boolean actionBarSummary = true;
    public boolean announcePurchases = true;
    public boolean debugLog = false;
    public List<String> failurePatterns = new ArrayList<>(List.of(
            "not enough",
            "insufficient",
            "no permission",
            "permission denied",
            "unknown item",
            "invalid item",
            "purchase failed",
            "buy failed",
            "cannot afford",
            "unknown command",
            "unknown or incomplete command",
            "command not found",
            "餘額不足",
            "余额不足",
            "金幣不足",
            "金币不足",
            "權限不足",
            "权限不足",
            "購買失敗",
            "购买失败",
            "未知物品",
            "物品不存在",
            "未知指令",
            "未知命令"
    ));

    public transient Set<String> whitelistSet = Set.of();
    public transient String loadWarning;

    public static AutoBuyConfig loadOrCreate() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        AutoBuyConfig defaults = new AutoBuyConfig();

        try {
            if (Files.notExists(path)) {
                defaults.normalize();
                defaults.write(path);
                return defaults;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            AutoBuyConfig loaded = GSON.fromJson(json, AutoBuyConfig.class);
            if (loaded == null) {
                throw new JsonSyntaxException("Config is empty");
            }
            loaded.normalize();
            return loaded;
        } catch (Exception e) {
            AutoBuyClient.LOGGER.error("Failed to load config {}, using built-in defaults", path, e);
            defaults.normalize();
            defaults.loadWarning = "讀取設定失敗，已使用內建預設值；請檢查 logs。";
            return defaults;
        }
    }

    private void write(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AutoBuyClient.LOGGER.error("Failed to write default config {}", path, e);
            loadWarning = "無法建立預設設定檔；請檢查 config 目錄權限。";
        }
    }

    public AutoBuyConfig copy() {
        AutoBuyConfig copy = GSON.fromJson(GSON.toJson(this), AutoBuyConfig.class);
        if (copy == null) {
            copy = new AutoBuyConfig();
        }
        copy.normalize();
        return copy;
    }

    public boolean save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            AutoBuyClient.LOGGER.error("Failed to save config {}", path, e);
            return false;
        }
    }

    public void normalize() {
        scanIntervalTicks = clamp(scanIntervalTicks, 1, 1200);
        commandCooldownMs = clamp(commandCooldownMs, 0, 60_000);
        maxCommandsPerScan = clamp(maxCommandsPerScan, 1, 32);
        maxItemsPerOrder = clamp(maxItemsPerOrder, 1, 1_000_000);
        pendingTimeoutSeconds = clamp(pendingTimeoutSeconds, 1, 3_600);
        pendingMaxRetries = clamp(pendingMaxRetries, 0, 10);
        stockHighStacks = clamp(stockHighStacks, 1, 64);
        stockLowStacks = clamp(stockLowStacks, 0, 63);
        if (stockLowStacks >= stockHighStacks) {
            stockLowStacks = stockHighStacks - 1;
        }
        stockTargetStacks = clamp(stockTargetStacks, 1, 64);
        if (stockTargetStacks < stockLowStacks) {
            stockTargetStacks = stockHighStacks;
        }

        if (printerGateMode == null) {
            printerGateMode = PrinterGateMode.PRINTER_ENABLED;
        }
        if (requiredCountMode == null) {
            requiredCountMode = RequiredCountMode.MISSING;
        }
        if (purchaseMode == null) {
            purchaseMode = PurchaseMode.BULK;
        }

        if (failurePatterns == null) {
            failurePatterns = new ArrayList<>();
        }
        List<String> patterns = new ArrayList<>();
        for (String pattern : failurePatterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            patterns.add(pattern.toLowerCase(Locale.ROOT));
        }
        failurePatterns = patterns;

        if (whitelist == null) {
            whitelist = new ArrayList<>();
        }
        LinkedHashSet<String> normalized = normalizeWhitelist(whitelist);
        whitelist = new ArrayList<>(normalized);
        whitelistSet = Set.copyOf(normalized);
    }

    public boolean saveWhitelist(Collection<String> entries) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        LinkedHashSet<String> normalized = normalizeWhitelist(entries);
        JsonObject root;

        try {
            if (Files.exists(path)) {
                JsonElement element = JsonParser.parseString(
                        Files.readString(path, StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    throw new JsonSyntaxException("Config root is not an object");
                }
                root = element.getAsJsonObject();
            } else {
                root = GSON.toJsonTree(this).getAsJsonObject();
            }
        } catch (Exception e) {
            AutoBuyClient.LOGGER.error("Failed to read config {} before saving whitelist", path, e);
            return false;
        }

        JsonArray array = new JsonArray();
        for (String id : normalized) {
            array.add(id);
        }
        root.add("whitelist", array);

        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AutoBuyClient.LOGGER.error("Failed to save whitelist to {}", path, e);
            return false;
        }

        whitelist = new ArrayList<>(normalized);
        whitelistSet = Set.copyOf(normalized);
        return true;
    }

    private static LinkedHashSet<String> normalizeWhitelist(Collection<String> entries) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (entries == null) {
            return normalized;
        }
        for (String raw : entries) {
            String id = ItemIds.normalizeWhitelistEntry(raw);
            if (id == null) {
                AutoBuyClient.LOGGER.warn("Ignoring invalid whitelist entry: {}", raw);
            } else {
                normalized.add(id);
            }
        }
        return normalized;
    }

    public boolean isWhitelisted(String itemId) {
        if (whitelistSet.isEmpty()) {
            return !autoSyncWhitelist && whitelistEmptyAllowAll;
        }
        return whitelistSet.contains(ItemIds.normalizeRegistryId(itemId));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

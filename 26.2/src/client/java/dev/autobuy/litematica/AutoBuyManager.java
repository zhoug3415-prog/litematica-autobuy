package dev.autobuy.litematica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class AutoBuyManager {
    private static final Logger LOG = AutoBuyClient.LOGGER;
    private static final long MANUAL_SCAN_INTERVAL_MS = 1_000L;
    private static final long FAILURE_MATCH_WINDOW_MS = 15_000L;

    private final Minecraft mc = Minecraft.getInstance();
    private AutoBuyConfig config;
    private final LitematicaBridge bridge = new LitematicaBridge();
    private final MaterialScanner scanner = new MaterialScanner(bridge);
    private final InventoryCounter inventoryCounter = new InventoryCounter();
    private final DeficitCalculator deficitCalculator = new DeficitCalculator();
    private final BuyCommandSender sender;
    private final PendingOrderTracker pending = new PendingOrderTracker();
    private final Feedback feedback;

    private final Queue<String> incomingMessages = new ConcurrentLinkedQueue<>();
    private final Set<String> blockedItems = new LinkedHashSet<>();
    private final Map<String, String> blockedReasons = new HashMap<>();

    private boolean autoEnabled;
    private boolean toggleRequested;
    private boolean scanRequested;
    private boolean reloadRequested;
    private boolean whitelistSyncRequested;
    private boolean purchaseModeToggleRequested;
    private String emptyMaterialListPlacement;
    private boolean emptyMaterialListWarned;
    private long materialListProgressStartedAtMs;
    private int materialListProgressStartRemaining;
    private int materialListLastRemaining;
    private long materialListLastProgressAtMs;
    private boolean materialListStallWarned;
    private long tickCounter;
    private long lastAutoScanTick = -1L;
    private long lastManualScanAtMs;
    private long globalBackoffUntilMs;
    private Object lastConnection;
    private int consecutiveErrors;
    private String lastSentItemId;
    private long lastSentAtMs;
    private String startupWarning;
    private LitematicaBridge.MaterialSelection queuedSelection;
    private LitematicaBridge.MaterialSelection adaptiveTarget;
    private Map<String, Integer> queuedMaxStackByItem = Map.of();
    private LitematicaBridge.MaterialSelection pendingManualScan;
    private LitematicaBridge.MaterialSelection pendingWhitelistSync;

    public AutoBuyManager(AutoBuyConfig config) {
        this.config = config;
        bridge.setAdaptiveWhitelist(config.autoSyncWhitelist);
        this.sender = new BuyCommandSender(config);
        this.feedback = new Feedback(config);
        this.startupWarning = config.loadWarning;
    }

    public AutoBuyConfig getConfig() {
        return config;
    }

    public boolean isAutoEnabled() {
        return autoEnabled;
    }

    public void setAutoEnabled(boolean enabled) {
        if (autoEnabled == enabled) {
            return;
        }
        autoEnabled = enabled;
        bridge.invalidateMaterialCount();
        if (enabled) {
            lastAutoScanTick = tickCounter - Math.max(1, config.scanIntervalTicks);
            feedback.chat("自動補料已開啟。");
            feedback.actionBar("自動補料：開啟");
        } else {
            sender.clear();
            feedback.chat("自動補料已關閉；已排入佇列的指令已清除。");
            feedback.actionBar("自動補料：關閉");
        }
    }

    public boolean applyGuiSettings(AutoBuyConfig next, boolean autoEnabled) {
        next.normalize();
        bridge.setAdaptiveWhitelist(next.autoSyncWhitelist);
        boolean modeOk = true;

        if (next.purchaseMode != config.purchaseMode) {
            modeOk = bridge.switchMaterialListType(
                    next.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER);
            if (!modeOk) {
                next.purchaseMode = config.purchaseMode;
            }
        }

        if (!next.save()) {
            bridge.setAdaptiveWhitelist(config.autoSyncWhitelist);
            feedback.error("設定儲存失敗", null);
            return false;
        }

        config = next;
        adaptiveTarget = null;
        cancelPendingMaterialRequests();
        bridge.invalidateMaterialCount();
        feedback.setConfig(config);
        sender.updateConfig(config);
        sender.clear();
        resetEmptyMaterialListState();
        setAutoEnabled(autoEnabled);

        if (modeOk) {
            feedback.chat("設定已儲存。");
        } else {
            feedback.chat("設定已儲存；購買模式未切換（計算中、未選取 placement，或精確模式尚未設為單層）。");
        }
        return modeOk;
    }

    public void requestToggle() {
        toggleRequested = true;
    }

    public void requestScan() {
        scanRequested = true;
    }

    public void requestReload() {
        reloadRequested = true;
    }

    public void requestWhitelistSync() {
        whitelistSyncRequested = true;
    }

    public void requestPurchaseModeToggle() {
        purchaseModeToggleRequested = true;
    }

    public void onGameMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (incomingMessages.size() >= 128) {
            incomingMessages.poll();
        }
        incomingMessages.add(message);
    }

    public void onClientTick(Minecraft client) {
        try {
            if (client.getConnection() == null) {
                if (lastConnection != null) {
                    resetConnectionState();
                }
                return;
            }
            if (lastConnection != client.getConnection()) {
                resetConnectionState();
                lastConnection = client.getConnection();
            }
            if (client.player == null || client.level == null) {
                return;
            }

            tickCounter++;
            if (startupWarning != null) {
                feedback.chat(startupWarning);
                startupWarning = null;
            }

            processReload();
            bridge.invalidateChangedSelection();
            processToggle();
            processPurchaseModeToggle();
            updateAdaptiveTarget();
            processWhitelistSync();
            processIncomingMessages();
            tickSender();
            tickPending();
            processScanRequest();
            runAutoScan();
        } catch (Exception | LinkageError e) {
            consecutiveErrors++;
            LOG.error("[AutoBuy] Tick failed", e);
            if (consecutiveErrors >= 5) {
                autoEnabled = false;
                sender.clear();
                consecutiveErrors = 0;
                feedback.error("連續發生錯誤，已安全停用自動補料；請查看日誌。", e);
            }
        }
    }

    private void cancelPendingMaterialRequests() {
        pendingManualScan = null;
        pendingWhitelistSync = null;
        scanRequested = false;
        whitelistSyncRequested = false;
    }

    private void resetConnectionState() {
        cancelPendingMaterialRequests();
        autoEnabled = false;
        whitelistSyncRequested = false;
        purchaseModeToggleRequested = false;
        sender.clear();
        queuedSelection = null;
        adaptiveTarget = null;
        bridge.resetMaterialTracking();
        pending.clear();
        blockedItems.clear();
        blockedReasons.clear();
        incomingMessages.clear();
        lastAutoScanTick = -1L;
        lastManualScanAtMs = 0L;
        globalBackoffUntilMs = 0L;
        consecutiveErrors = 0;
        lastSentItemId = null;
        lastSentAtMs = 0L;
        lastConnection = null;
        feedback.clearThrottles();
        resetEmptyMaterialListState();
    }

    private void processReload() {
        if (!reloadRequested) {
            return;
        }
        reloadRequested = false;
        cancelPendingMaterialRequests();

        config = AutoBuyConfig.loadOrCreate();
        bridge.setAdaptiveWhitelist(config.autoSyncWhitelist);
        adaptiveTarget = null;
        lastAutoScanTick = -1L;
        sender.clear();
        bridge.invalidateMaterialCount();
        feedback.setConfig(config);
        sender.updateConfig(config);
        feedback.clearThrottles();
        blockedItems.clear();
        blockedReasons.clear();
        pending.clearAttemptHistory();
        consecutiveErrors = 0;
        resetEmptyMaterialListState();

        feedback.chat("設定已重新載入；白名單 " + config.whitelist.size()
                + " 項，失敗封鎖已解除。");
        if (config.loadWarning != null) {
            feedback.chat(config.loadWarning);
        }
        feedback.debug("Config reloaded: gate=" + config.printerGateMode
                + ", count=" + config.requiredCountMode
                + ", cooldownMs=" + config.commandCooldownMs
                + ", maxCommands=" + config.maxCommandsPerScan
                + ", maxPerOrder=" + config.maxItemsPerOrder);
    }

    private void processToggle() {
        if (!toggleRequested) {
            return;
        }
        toggleRequested = false;
        setAutoEnabled(!autoEnabled);
    }

    private void processPurchaseModeToggle() {
        if (!purchaseModeToggleRequested) {
            return;
        }
        purchaseModeToggleRequested = false;
        if (mc.player == null) {
            return;
        }

        AutoBuyConfig.PurchaseMode next = config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER
                ? AutoBuyConfig.PurchaseMode.BULK
                : AutoBuyConfig.PurchaseMode.EXACT_LAYER;
        boolean renderLayers = next == AutoBuyConfig.PurchaseMode.EXACT_LAYER;
        if (renderLayers && bridge.getMaterialSelection(true) == null) {
            showSingleLayerRequired();
            return;
        }

        if (!bridge.switchMaterialListType(renderLayers)) {
            feedback.chat("無法切換購買模式：材料清單正在計算，或尚未選取 placement；"
                    + "請等進度完成／選取後再按一次。");
            feedback.actionBar("購買模式切換失敗");
            return;
        }

        config.purchaseMode = next;
        cancelPendingMaterialRequests();
        sender.clear();
        resetEmptyMaterialListState();
        String label = next == AutoBuyConfig.PurchaseMode.EXACT_LAYER ? "精確單層" : "大量";
        String listType = renderLayers ? "渲染層" : "全部";
        feedback.chat("購買模式已切換為「" + label + "」；Litematica 材料清單類型已切到「"
                + listType + "」，必要時會重新計數，請看進度／ETA。");
        feedback.actionBar("購買模式：" + label);
    }

    private void updateAdaptiveTarget() {
        if (!config.autoSyncWhitelist) {
            return;
        }
        LitematicaBridge.MaterialSelection target = bridge.getMaterialSelection(
                config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER);
        if (Objects.equals(adaptiveTarget, target)) {
            return;
        }
        adaptiveTarget = target;
        sender.clear();
        bridge.invalidateChangedSelection();
        lastAutoScanTick = -1L;
        resetEmptyMaterialListState();
        if (target != null) {
            feedback.chatThrottled("adaptive-target",
                    "自適應白名單：目標「" + target.placement().getName()
                            + "」，清單完成後將取代舊白名單。", 5_000L);
        } else {
            feedback.actionBar("自適應白名單：無可用目標，已暫停購買並保留原名單。");
        }
    }

    private void processWhitelistSync() {
        if (pendingWhitelistSync != null) {
            if (!bridge.isCurrentSelection(pendingWhitelistSync)) {
                pendingWhitelistSync = null;
                whitelistSyncRequested = false;
                feedback.actionBar("單層選取已變更，已取消待執行的白名單同步。");
                return;
            }
            if (!bridge.canResumeMaterialScan()) {
                if (tickCounter % 20 == 0) {
                    updateMaterialListProgress(System.currentTimeMillis(), emptyMaterialListPlacement);
                }
                return;
            }
            pendingWhitelistSync = null;
            whitelistSyncRequested = true;
        }
        if (!whitelistSyncRequested) {
            return;
        }
        whitelistSyncRequested = false;
        if (mc.player == null) {
            return;
        }
        sender.clear();

        MaterialScanner.ScanResult scan = scanner.scan(config.purchaseMode, config.requiredCountMode);
        switch (scan.status()) {
            case NO_PLACEMENT -> {
                resetEmptyMaterialListState();
                feedback.chat("白名單同步：未選取 Litematica placement。");
                feedback.actionBar("白名單同步：未選取 placement");
                return;
            }
            case SINGLE_LAYER_REQUIRED -> {
                showSingleLayerRequired();
                return;
            }
            case LIST_EMPTY -> {
                if (config.autoSyncWhitelist || config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER) {
                    pendingWhitelistSync = bridge.getMaterialSelection(config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER);
                }
                handleEmptyMaterialList(scan);
                return;
            }
            case ERROR -> {
                resetEmptyMaterialListState();
                feedback.chat("白名單同步失敗：" + scan.error());
                feedback.actionBar("白名單同步失敗");
                return;
            }
            case OK -> {
                if (emptyMaterialListWarned && !config.autoSyncWhitelist
                        && config.purchaseMode == AutoBuyConfig.PurchaseMode.BULK) {
                    feedback.chat("材料清單已就緒；開始同步白名單。");
                }
                resetEmptyMaterialListState();
            }
        }

        if (scan.renderLayersOnly()) {
            feedback.chat("注意：Litematica 材料清單目前只包含渲染層，同步僅處理該範圍。");
        }

        SyncOutcome outcome = applyWhitelistSync(scan, true);
        // 手動同步後暫緩自動掃描一輪，避免同步完立刻下單。
        lastAutoScanTick = tickCounter;
        if (outcome.changed() && outcome.saved()) {
            feedback.actionBar("白名單同步：+" + outcome.added() + " / -" + outcome.removed());
        }
    }

    private SyncOutcome applyWhitelistSync(MaterialScanner.ScanResult scan, boolean manual) {
        LinkedHashSet<String> updated = scan.missingByItem().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> previous = Set.copyOf(config.whitelist);
        int added = (int) updated.stream().filter(id -> !previous.contains(id)).count();
        int removed = (int) previous.stream().filter(id -> !updated.contains(id)).count();

        if (added == 0 && removed == 0) {
            if (manual) {
                feedback.chat("白名單同步：已是最新，無變更。");
                feedback.actionBar("白名單同步：無變更");
            }
            return new SyncOutcome(false, 0, 0, true);
        }

        boolean saved = config.saveWhitelist(updated);
        if (!saved) {
            sender.clear();
            String message = "白名單同步失敗：無法寫入 config，白名單未變更；已停止本輪購買。";
            if (manual) {
                feedback.chat(message);
                feedback.actionBar("白名單同步：寫入失敗");
            } else {
                feedback.chatThrottled("auto-whitelist-save-fail", message, 30_000L);
                feedback.actionBar("白名單同步：寫入失敗");
            }
            return new SyncOutcome(true, added, removed, false);
        }

        // 白名單已變更，丟棄尚未送出的舊佇列，下一輪重新依新名單評估。
        sender.clear();

        if (manual) {
            feedback.chat("白名單同步完成：新增 " + added + "、移除 " + removed
                    + "；目前 " + config.whitelist.size() + " 項，已寫入 config。");
        } else {
            feedback.debug("Auto whitelist sync: added=" + added + ", removed=" + removed);
        }
        return new SyncOutcome(true, added, removed, true);
    }

    private void handleEmptyMaterialList(MaterialScanner.ScanResult scan) {
        String name = scan.placementName() == null ? "unknown" : scan.placementName();
        long now = System.currentTimeMillis();
        if (!name.equals(emptyMaterialListPlacement)) {
            emptyMaterialListPlacement = name;
            emptyMaterialListWarned = false;
            resetMaterialListProgressTracking();
        }

        if (!emptyMaterialListWarned) {
            if (!config.autoSyncWhitelist && config.purchaseMode == AutoBuyConfig.PurchaseMode.BULK) {
                feedback.chat("材料清單尚未計算完成（placement: " + name
                        + "）；進度與 ETA 會顯示在 action bar；若停滯會提示載入區塊。");
            }
            emptyMaterialListWarned = true;
        }

        updateMaterialListProgress(now, name);
    }

    private void updateMaterialListProgress(long nowMs, String placementName) {
        LitematicaBridge.MaterialListProgress progress = bridge.getMaterialListProgress();
        if (!progress.known()) {
            feedback.actionBar("材料清單準備中…（等待 Litematica 計數任務）");
            return;
        }

        int total = progress.totalChunks();
        int remaining = Math.min(progress.remainingChunks(), total);
        int done = Math.max(0, total - remaining);
        int percent = total > 0 ? (int) Math.round(done * 100.0 / total) : 0;

        if (materialListProgressStartedAtMs == 0L) {
            materialListProgressStartedAtMs = nowMs;
            materialListProgressStartRemaining = remaining;
            materialListLastRemaining = remaining;
            materialListLastProgressAtMs = nowMs;
        }
        if (remaining < materialListLastRemaining) {
            materialListLastRemaining = remaining;
            materialListLastProgressAtMs = nowMs;
            materialListStallWarned = false;
        }

        boolean stalled = remaining > 0 && nowMs - materialListLastProgressAtMs > 5_000L;
        long etaMs = -1L;
        long elapsed = nowMs - materialListProgressStartedAtMs;
        int doneSinceStart = materialListProgressStartRemaining - remaining;
        if (doneSinceStart > 0 && elapsed > 0) {
            etaMs = (long) remaining * elapsed / doneSinceStart;
        }

        String eta;
        if (remaining <= 0) {
            eta = "完成中";
        } else if (stalled) {
            eta = "等待區塊載入";
        } else if (etaMs >= 0) {
            eta = "ETA 約 " + formatDuration(etaMs);
        } else {
            eta = "ETA 計算中";
        }

        feedback.actionBar("材料清單 " + percent + "%（剩餘 " + remaining + "/" + total
                + " 區塊，" + eta + "）");

        if (stalled && !materialListStallWarned) {
            feedback.chat("材料清單計數停滯：剩餘 " + remaining + " 個區塊（placement: "
                    + placementName + "）；請靠近 placement 或讓該區域的區塊載入。");
            materialListStallWarned = true;
        }
    }

    private static String formatDuration(long ms) {
        long totalSeconds = Math.max(1L, (ms + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private void resetEmptyMaterialListState() {
        emptyMaterialListPlacement = null;
        emptyMaterialListWarned = false;
        resetMaterialListProgressTracking();
    }

    private void resetMaterialListProgressTracking() {
        materialListProgressStartedAtMs = 0L;
        materialListProgressStartRemaining = 0;
        materialListLastRemaining = 0;
        materialListLastProgressAtMs = 0L;
        materialListStallWarned = false;
    }

    private void processScanRequest() {
        boolean resuming = pendingManualScan != null;
        if (resuming) {
            if (!bridge.isCurrentSelection(pendingManualScan)) {
                pendingManualScan = null;
                scanRequested = false;
                feedback.actionBar("單層選取已變更，已取消待執行的手動購買。");
                return;
            }
            if (!bridge.canResumeMaterialScan()) {
                if (tickCounter % 20 == 0) {
                    updateMaterialListProgress(System.currentTimeMillis(), emptyMaterialListPlacement);
                }
                return;
            }
            pendingManualScan = null;
            scanRequested = true;
        }
        if (!scanRequested) {
            return;
        }
        scanRequested = false;

        long now = System.currentTimeMillis();
        if (!resuming && now - lastManualScanAtMs < MANUAL_SCAN_INTERVAL_MS) {
            feedback.actionBar("掃描過於頻繁，請稍候。");
            return;
        }
        lastManualScanAtMs = now;
        lastAutoScanTick = tickCounter;
        scanAndEnqueue(now, true);
    }

    private void runAutoScan() {
        if ((!autoEnabled && !config.autoSyncWhitelist)
                || pendingManualScan != null || pendingWhitelistSync != null) {
            return;
        }
        if (lastAutoScanTick >= 0
                && tickCounter - lastAutoScanTick < Math.max(1, config.scanIntervalTicks)) {
            return;
        }
        lastAutoScanTick = tickCounter;
        scanAndEnqueue(System.currentTimeMillis(), false);
    }

    private void tickSender() {
        if ((config.autoSyncWhitelist || config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER)
                && !sender.isEmpty()
                && !bridge.isCurrentSelection(queuedSelection)) {
            sender.clear();
            feedback.actionBar("單層／選取範圍已變更，已清除舊購買佇列。");
            return;
        }
        if (!sender.isEmpty() && !isPrinterGateOpen()) {
            sender.clear();
            feedback.actionBar("Printer 狀態不符，已停止送出待處理購買。");
            return;
        }
        if ((config.autoSyncWhitelist || config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER)
                && !sender.isEmpty()
                && config.skipWhenInventoryFull) {
            InventoryCounter.CountResult inventory = inventoryCounter.count(mc, config.countOffhand);
            Map<String, Integer> reserved = sender.queuedCounts();
            pending.requestedCounts().forEach((item, count) -> reserved.merge(item, count, Integer::sum));
            if (requiredMainSlots(reserved, queuedMaxStackByItem, inventory.partialRoom())
                    > inventory.emptySlots()) {
                sender.clear();
                feedback.actionBar("背包空間不足，暫停；等空格足夠後重新計算。");
                return;
            }
        }
        long now = System.currentTimeMillis();
        List<BuyCommandSender.Order> sent = sender.tick(mc, now);
        String sendError = sender.consumeLastError();
        if (sendError != null) {
            feedback.error(sendError, null);
        }
        if (sent.isEmpty()) {
            return;
        }

        InventoryCounter.CountResult inventory = inventoryCounter.count(mc, config.countOffhand);
        for (BuyCommandSender.Order order : sent) {
            int baseline = inventory.counts().getOrDefault(order.itemId(), 0);
            pending.record(order.itemId(), order.count(), baseline, now);
            lastSentItemId = order.itemId();
            lastSentAtMs = now;

            if (config.announcePurchases) {
                feedback.chat("已送出：/buy " + order.itemId() + " " + order.count());
            }
            feedback.debug("Pending " + order.itemId() + " x" + order.count()
                    + " baseline=" + baseline);
        }
    }

    private boolean isPrinterGateOpen() {
        return switch (bridge.getPrinterState(config.printerGateMode)) {
            case ACTIVE, ENABLED, PRINTING -> true;
            default -> false;
        };
    }

    private void tickPending() {
        if (mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, Integer> counts = inventoryCounter.count(mc, config.countOffhand).counts();
        List<PendingOrderTracker.Event> events = pending.update(
                now,
                counts,
                config.pendingTimeoutSeconds * 1000L,
                config.pendingMaxRetries
        );

        for (PendingOrderTracker.Event event : events) {
            switch (event.type()) {
                case DELIVERED -> feedback.debug("Delivered: " + event.itemId());
                case TIMEOUT_RETRY -> feedback.chatThrottled(
                        "timeout:" + event.itemId(),
                        "物品 " + event.itemId() + "（原購買 " + event.requestedCount()
                                + "）逾時未到帳，允許重試（第 " + event.attempt() + " 次）。",
                        5_000L
                );
                case RETRY_EXHAUSTED -> {
                    blockedItems.add(event.itemId());
                    blockedReasons.put(event.itemId(), "多次逾時未到帳");
                    feedback.chat("物品 " + event.itemId() + " 連續 " + event.attempt()
                            + " 次未到帳，已停止自動購買；重新載入設定可解除。");
                    LOG.warn("[AutoBuy] Giving up on {} after {} attempts", event.itemId(),
                            event.attempt());
                }
            }
        }
    }

    private void processIncomingMessages() {
        String text;
        int processed = 0;

        while (processed++ < 20 && (text = incomingMessages.poll()) != null) {
            String lower = text.toLowerCase(Locale.ROOT);
            boolean failure = false;
            for (String pattern : config.failurePatterns) {
                if (!pattern.isEmpty() && lower.contains(pattern)) {
                    failure = true;
                    break;
                }
            }
            if (!failure) {
                continue;
            }

            long now = System.currentTimeMillis();
            boolean permission = mentionsPermission(lower);
            PendingOrderTracker.PendingOrder target = pending.findMentioned(lower)
                    .orElseGet(() -> pending.mostRecent().orElse(null));

            String targetId = target != null ? target.itemId() : null;
            if (targetId == null && lastSentItemId != null
                    && now - lastSentAtMs <= FAILURE_MATCH_WINDOW_MS) {
                targetId = lastSentItemId;
            }

            sender.clear();
            if (permission) {
                globalBackoffUntilMs = Math.max(globalBackoffUntilMs, now + 60_000L);
            }

            if (targetId != null) {
                pending.removeForFailure(targetId);
                blockedItems.add(targetId);
                blockedReasons.put(targetId, "伺服器回報購買失敗");
                feedback.chat("偵測到 /buy 失敗，已停止購買 " + targetId
                        + "；訊息：" + shorten(text));
                if (permission) {
                    feedback.chat("權限相關錯誤：暫停送出 60 秒。");
                }
                LOG.warn("[AutoBuy] Purchase failure for {}: {}", targetId, shorten(text));
            } else {
                long backoff = permission ? 60_000L : 10_000L;
                globalBackoffUntilMs = Math.max(globalBackoffUntilMs, now + backoff);
                feedback.chatThrottled(
                        "server-error",
                        "偵測到伺服器錯誤訊息，暫停送出 " + (backoff / 1000L)
                                + " 秒；訊息：" + shorten(text),
                        10_000L
                );
                LOG.warn("[AutoBuy] Server failure message without a pending item: {}",
                        shorten(text));
            }
            break;
        }
    }

    private boolean canPurchase(long now) {
        if (now < globalBackoffUntilMs) {
            long seconds = Math.max(1L, (globalBackoffUntilMs - now + 999L) / 1000L);
            feedback.actionBar("伺服器錯誤冷卻中：" + seconds + " 秒");
            return false;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            feedback.chatThrottled("creative", "創造／旁觀模式不自動購買。", 30_000L);
            return false;
        }
        LitematicaBridge.PrinterState printerState = bridge.getPrinterState(config.printerGateMode);
        switch (printerState) {
            case NOT_INSTALLED -> {
                feedback.chatThrottled("no-printer",
                        "未偵測到 Litematica Printer；預設 gate 無法運作。"
                                + "可將 printerGateMode 設為 ALWAYS。", 30_000L);
                return false;
            }
            case UNAVAILABLE -> {
                feedback.chatThrottled("printer-api",
                        "Litematica Printer API 不相容或讀取失敗，已安全跳過。", 30_000L);
                return false;
            }
            case DISABLED -> {
                feedback.chatThrottled("printer-off",
                        "Printer 未啟用（Work Switch 或 Print 未開），跳過本輪。", 15_000L);
                return false;
            }
            case IDLE -> {
                feedback.chatThrottled("printer-idle",
                        "Printer 目前未在放置方塊，跳過本輪。", 15_000L);
                return false;
            }
            case ACTIVE, ENABLED, PRINTING -> {
                return true;
            }
        }
        return false;
    }

    private void scanAndEnqueue(long now, boolean manual) {
        if ((config.autoSyncWhitelist || config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER)
                && !sender.isEmpty()) {
            feedback.actionBar("補料佇列傳送中 " + sender.size());
            return;
        }
        if (config.skipWhenScreenOpen && mc.gui != null && mc.gui.screen() != null) {
            feedback.actionBar("GUI 開啟中，暫停掃描。");
            return;
        }
        if (!config.autoSyncWhitelist && !canPurchase(now)) {
            return;
        }

        MaterialScanner.ScanResult scan = scanner.scan(config.purchaseMode, config.requiredCountMode);
        switch (scan.status()) {
            case NO_PLACEMENT -> {
                resetEmptyMaterialListState();
                feedback.chatThrottled("no-placement", "未選取 Litematica placement。", 15_000L);
                return;
            }
            case SINGLE_LAYER_REQUIRED -> {
                showSingleLayerRequired();
                return;
            }
            case LIST_EMPTY -> {
                if (manual && (config.autoSyncWhitelist
                        || config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER)) {
                    pendingManualScan = bridge.getMaterialSelection(config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER);
                }
                handleEmptyMaterialList(scan);
                return;
            }
            case ERROR -> {
                resetEmptyMaterialListState();
                feedback.chatThrottled("scan-error",
                        "材料掃描失敗：" + scan.error(), 15_000L);
                return;
            }
            case OK -> {
                if (emptyMaterialListWarned && !config.autoSyncWhitelist
                        && config.purchaseMode == AutoBuyConfig.PurchaseMode.BULK) {
                    feedback.chat("材料清單已就緒（" + scan.required().size()
                            + " 種材料）；開始計算缺料。");
                }
                resetEmptyMaterialListState();
            }
        }

        feedback.debug("Scanned placement: " + scan.placementName()
                + ", material kinds=" + scan.required().size());
        consecutiveErrors = 0;

        if (config.autoSyncWhitelist) {
            SyncOutcome syncOutcome = applyWhitelistSync(scan, false);
            if (!syncOutcome.saved()) {
                return;
            }
            if (syncOutcome.changed()) {
                if (manual) {
                    pendingManualScan = bridge.getMaterialSelection(
                            config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER);
                }
                feedback.actionBar("自適應白名單已更新：" + scan.placementName()
                        + " | " + config.whitelist.size() + " 項");
                return;
            }
            if (!manual && !autoEnabled) {
                feedback.actionBar("自適應白名單：" + scan.placementName()
                        + " | " + config.whitelist.size() + " 項 | 自動購買關閉");
                return;
            }
            if (!canPurchase(now)) {
                return;
            }
        }

        if (scan.required().isEmpty()) {
            feedback.actionBar("無缺料（missing=0）");
            return;
        }

        InventoryCounter.CountResult inventory = inventoryCounter.count(mc, config.countOffhand);
        DeficitCalculator.Result result = deficitCalculator.calculate(
                scan.required(), inventory.counts(), config);
        if (result.deficits().isEmpty()) {
            feedback.actionBar("無缺料（庫存已足夠）");
            return;
        }
        pending.retainAttempts(result.deficits().stream()
                .map(DeficitCalculator.Deficit::itemId)
                .collect(Collectors.toSet()));

        int pendingBlocked = 0;
        int blocked = 0;
        int stockGateSkipped = 0;
        List<DeficitCalculator.Deficit> sendable = new ArrayList<>();
        Map<String, Integer> plannedCounts = new HashMap<>();

        for (DeficitCalculator.Deficit deficit : result.deficits()) {
            if (!deficit.whitelisted()) {
                continue;
            }
            if (blockedItems.contains(deficit.itemId())) {
                blocked++;
                continue;
            }
            if (pending.isPending(deficit.itemId())) {
                pendingBlocked++;
                continue;
            }

            int desired = deficit.missing();
            if (config.stockGateEnabled
                    && config.purchaseMode == AutoBuyConfig.PurchaseMode.BULK) {
                int maxStack = Math.max(1,
                        scan.maxStackByItem().getOrDefault(deficit.itemId(), 64));
                long high = (long) config.stockHighStacks * maxStack;
                long low = (long) config.stockLowStacks * maxStack;
                long target = (long) config.stockTargetStacks * maxStack;
                int available = deficit.available();

                if (available >= high || available > low) {
                    stockGateSkipped++;
                    continue;
                }

                long toTarget = Math.max(0L, target - available);
                desired = (int) Math.min(Integer.MAX_VALUE,
                        Math.min(toTarget, deficit.missing()));
                if (desired <= 0) {
                    stockGateSkipped++;
                    continue;
                }
            }

            plannedCounts.put(deficit.itemId(), desired);
            sendable.add(deficit);
        }

        if (!sender.isEmpty()) {
            if (config.skipWhenInventoryFull && inventory.emptySlots() == 0) {
                sender.clear();
                feedback.chatThrottled("inv-full",
                        "主背包已滿，暫停購買；等空間足夠後會自動繼續。", 10_000L);
                feedback.actionBar("背包已滿，暫停");
                return;
            }
            feedback.actionBar(summary(scan, result, 0, pendingBlocked, 0, blocked,
                    stockGateSkipped, true));
            return;
        }

        int capBlocked = 0;
        List<BuyCommandSender.Order> plannedOrders = new ArrayList<>();
        for (DeficitCalculator.Deficit deficit : sendable) {
            if (plannedOrders.size() >= config.maxCommandsPerScan) {
                capBlocked++;
                continue;
            }
            int count = Math.min(
                    plannedCounts.getOrDefault(deficit.itemId(), deficit.missing()),
                    config.maxItemsPerOrder);
            if (count > 0) {
                plannedOrders.add(new BuyCommandSender.Order(deficit.itemId(), count));
            }
        }

        if (config.skipWhenInventoryFull && !plannedOrders.isEmpty()) {
            Map<String, Integer> reserved = new HashMap<>();
            for (BuyCommandSender.Order order : plannedOrders) {
                reserved.merge(order.itemId(), order.count(), (left, right) ->
                        (int) Math.min(Integer.MAX_VALUE, (long) left + right));
            }
            pending.requestedCounts().forEach((itemId, count) ->
                    reserved.merge(itemId, count, (left, right) ->
                            (int) Math.min(Integer.MAX_VALUE, (long) left + right)));

            int requiredSlots = requiredMainSlots(reserved, scan.maxStackByItem(),
                    inventory.partialRoom());
            if (requiredSlots > inventory.mainSlotCount()) {
                sender.clear();
                feedback.chatThrottled("inv-too-large",
                        "本輪購買需求需要 " + requiredSlots + " 格，超過主背包 "
                                + inventory.mainSlotCount()
                                + " 格；請降低 maxItemsPerOrder 或每輪指令數。",
                        30_000L);
                feedback.actionBar("背包容量不足：請降低單次上限");
                return;
            }
            if (requiredSlots > inventory.emptySlots()) {
                sender.clear();
                feedback.chatThrottled("inv-capacity",
                        "背包空間不足：本輪含待送達需要 " + requiredSlots + " 格，目前可用 "
                                + inventory.emptySlots()
                                + " 格；等空間足夠後會自動繼續購買。",
                        10_000L);
                feedback.actionBar("背包空間不足，暫停；等空格足夠");
                return;
            }
        }

        queuedSelection = config.autoSyncWhitelist || config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER
                ? bridge.getMaterialSelection(config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER) : null;
        queuedMaxStackByItem = scan.maxStackByItem();
        int enqueued = 0;
        for (BuyCommandSender.Order order : plannedOrders) {
            if (sender.enqueue(order.itemId(), order.count())) {
                enqueued++;
            } else {
                capBlocked++;
            }
        }

        feedback.actionBar(summary(scan, result, enqueued, pendingBlocked, capBlocked, blocked,
                stockGateSkipped, false));

        if (!result.notWhitelisted().isEmpty()) {
            String ids = result.deficits().stream()
                    .filter(deficit -> !deficit.whitelisted())
                    .limit(5)
                    .map(deficit -> deficit.itemId() + " x" + deficit.missing())
                    .collect(Collectors.joining(", "));
            int extra = Math.max(0, result.notWhitelisted().size() - 5);
            feedback.chatThrottled("whitelist",
                    "白名單阻擋 " + result.notWhitelisted().size() + " 項：" + ids
                            + (extra > 0 ? " …" : ""),
                    15_000L);
        }
        if (capBlocked > 0) {
            feedback.chatThrottled("cap",
                    "本輪達上限 " + config.maxCommandsPerScan + " 條，剩餘 "
                            + capBlocked + " 項下輪處理。",
                    10_000L);
        }
        if (stockGateSkipped > 0) {
            feedback.chatThrottled("stock-gate",
                    "庫存門檻跳過 " + stockGateSkipped + " 項（>= "
                            + config.stockHighStacks + " 組不買；<= "
                            + config.stockLowStacks + " 組才補到 "
                            + config.stockTargetStacks + " 組，且不超過缺額）。",
                    15_000L);
        }
        if (blocked > 0) {
            feedback.debug("Blocked item count=" + blocked + ", reasons=" + blockedReasons);
        }
        if (enqueued > 0) {
            feedback.debug("Enqueued=" + enqueued + ", pending=" + pending.pendingCount());
        }
    }

    private void showSingleLayerRequired() {
        sender.clear();
        resetEmptyMaterialListState();
        feedback.chatThrottled("single-layer-required",
                "精確單層模式只支援 Litematica「層級：單層」；請先選取 placement 並切到單層。"
                        + "下方所有、上方所有與範圍模式不會購買。", 15_000L);
        feedback.actionBar("精確單層：請切換至「層級：單層」");
    }

    private static int requiredMainSlots(Map<String, Integer> quantities,
                                         Map<String, Integer> maxStackByItem,
                                         Map<String, Integer> partialRoom) {
        int slots = 0;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            int quantity = Math.max(0, entry.getValue());
            if (quantity == 0) {
                continue;
            }
            int maxStack = Math.max(1, maxStackByItem.getOrDefault(entry.getKey(), 64));
            int room = Math.max(0, partialRoom.getOrDefault(entry.getKey(), 0));
            int remaining = Math.max(0, quantity - room);
            slots += (remaining + maxStack - 1) / maxStack;
        }
        return slots;
    }

    private String summary(MaterialScanner.ScanResult scan, DeficitCalculator.Result result,
                           int enqueued, int pendingBlocked, int capBlocked, int blocked,
                           int stockGateSkipped, boolean queueBusy) {
        StringBuilder builder = new StringBuilder();
        builder.append("缺料 ").append(result.deficits().size()).append(" 種");
        if (queueBusy) {
            builder.append(" | 佇列傳送中 ").append(sender.size());
            long remaining = sender.cooldownRemainingMs(System.currentTimeMillis());
            if (remaining > 0) {
                builder.append(" | 冷卻 ").append((remaining + 999L) / 1000L).append("s");
            }
        } else {
            builder.append(" | 已排入 ").append(enqueued);
        }
        builder.append(" | 待送達 ").append(pending.pendingCount());
        if (pendingBlocked > 0) {
            builder.append(" | 待送達略過 ").append(pendingBlocked);
        }
        if (!result.notWhitelisted().isEmpty()) {
            builder.append(" | 白名單 ").append(result.notWhitelisted().size());
        }
        if (blocked > 0) {
            builder.append(" | 失敗封鎖 ").append(blocked);
        }
        if (stockGateSkipped > 0) {
            builder.append(" | 庫存門檻 ").append(stockGateSkipped);
        }
        if (capBlocked > 0) {
            builder.append(" | 上限 ").append(capBlocked);
        }
        if (config.purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER) {
            builder.append(" | 精確層");
            String layerMode = bridge.getRenderLayerModeName();
            if (!layerMode.isEmpty()) {
                builder.append("（").append(layerMode).append("）");
            }
        } else {
            builder.append(" | 大量");
        }
        return builder.toString();
    }

    private record SyncOutcome(boolean changed, int added, int removed, boolean saved) {
    }

    private static boolean mentionsPermission(String lower) {
        return lower.contains("permission")
                || lower.contains("not allowed")
                || lower.contains("權限")
                || lower.contains("权限");
    }

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 157) + "...";
    }
}

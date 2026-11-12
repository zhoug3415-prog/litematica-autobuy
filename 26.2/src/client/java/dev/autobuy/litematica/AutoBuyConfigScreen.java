package dev.autobuy.litematica;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public final class AutoBuyConfigScreen {
    private AutoBuyConfigScreen() {
    }

    public static Screen create(Screen parent) {
        AutoBuyManager manager = AutoBuyClient.getManager();
        if (manager == null) {
            return parent;
        }

        AutoBuyConfig settings = manager.getConfig().copy();
        boolean[] autoEnabled = {manager.isAutoEnabled()};

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Litematica AutoBuy 設定"))
                .setSavingRunnable(() -> manager.applyGuiSettings(settings, autoEnabled[0]));
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("一般"));
        general.addEntry(entries.startBooleanToggle(Component.literal("自動補料"), autoEnabled[0])
                .setDefaultValue(false)
                .setTooltip(Component.literal("開啟後依掃描間隔自動補料；儲存後立即套用。"))
                .setSaveConsumer(value -> autoEnabled[0] = value)
                .build());
        general.addEntry(entries.startEnumSelector(Component.literal("購買模式"),
                        AutoBuyConfig.PurchaseMode.class, settings.purchaseMode)
                .setEnumNameProvider(value -> Component.literal(
                        value == AutoBuyConfig.PurchaseMode.EXACT_LAYER ? "精確單層" : "大量"))
                .setDefaultValue(AutoBuyConfig.PurchaseMode.BULK)
                .setTooltip(Component.literal("精確單層只支援層級：單層；本層未放置材料清單減去身上物品＝購買缺額，錯放不計。大量模式維持庫存門檻。"))
                .setSaveConsumer(value -> settings.purchaseMode = value)
                .build());
        general.addEntry(entries.startIntField(Component.literal("掃描間隔 (tick)"), settings.scanIntervalTicks)
                .setMin(1)
                .setMax(1200)
                .setDefaultValue(40)
                .setSaveConsumer(value -> settings.scanIntervalTicks = value)
                .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("副手計入庫存"), settings.countOffhand)
                .setDefaultValue(true)
                .setSaveConsumer(value -> settings.countOffhand = value)
                .build());

        ConfigCategory limits = builder.getOrCreateCategory(Component.literal("購買限制"));
        limits.addEntry(entries.startIntField(Component.literal("單次購買上限"), settings.maxItemsPerOrder)
                .setMin(1)
                .setMax(1_000_000)
                .setDefaultValue(576)
                .setTooltip(Component.literal("單一 /buy 指令的最大數量。"))
                .setSaveConsumer(value -> settings.maxItemsPerOrder = value)
                .build());
        limits.addEntry(entries.startIntField(Component.literal("每輪最多指令數"), settings.maxCommandsPerScan)
                .setMin(1)
                .setMax(32)
                .setDefaultValue(4)
                .setSaveConsumer(value -> settings.maxCommandsPerScan = value)
                .build());
        limits.addEntry(entries.startIntField(Component.literal("指令冷卻 (ms)"), settings.commandCooldownMs)
                .setMin(0)
                .setMax(60_000)
                .setDefaultValue(1000)
                .setSaveConsumer(value -> settings.commandCooldownMs = value)
                .build());
        limits.addEntry(entries.startIntField(Component.literal("待送達逾時 (秒)"), settings.pendingTimeoutSeconds)
                .setMin(1)
                .setMax(3600)
                .setDefaultValue(30)
                .setSaveConsumer(value -> settings.pendingTimeoutSeconds = value)
                .build());
        limits.addEntry(entries.startIntField(Component.literal("待送達重試次數"), settings.pendingMaxRetries)
                .setMin(0)
                .setMax(10)
                .setDefaultValue(2)
                .setSaveConsumer(value -> settings.pendingMaxRetries = value)
                .build());

        ConfigCategory stock = builder.getOrCreateCategory(Component.literal("庫存門檻"));
        stock.addEntry(entries.startBooleanToggle(Component.literal("啟用庫存門檻"), settings.stockGateEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("大量模式使用：>=4 組不買、1–4 組等待、<=1 組補到 4 組。"))
                .setSaveConsumer(value -> settings.stockGateEnabled = value)
                .build());
        stock.addEntry(entries.startIntField(Component.literal("停止購買組數"), settings.stockHighStacks)
                .setMin(1)
                .setMax(64)
                .setDefaultValue(4)
                .setSaveConsumer(value -> settings.stockHighStacks = value)
                .build());
        stock.addEntry(entries.startIntField(Component.literal("觸發購買組數"), settings.stockLowStacks)
                .setMin(0)
                .setMax(63)
                .setDefaultValue(1)
                .setSaveConsumer(value -> settings.stockLowStacks = value)
                .build());
        stock.addEntry(entries.startIntField(Component.literal("目標組數"), settings.stockTargetStacks)
                .setMin(1)
                .setMax(64)
                .setDefaultValue(4)
                .setSaveConsumer(value -> settings.stockTargetStacks = value)
                .build());

        ConfigCategory safety = builder.getOrCreateCategory(Component.literal("安全與回饋"));
        safety.addEntry(entries.startBooleanToggle(Component.literal("GUI 開啟時暫停"), settings.skipWhenScreenOpen)
                .setDefaultValue(true)
                .setSaveConsumer(value -> settings.skipWhenScreenOpen = value)
                .build());
        safety.addEntry(entries.startBooleanToggle(Component.literal("容量不足時等待"), settings.skipWhenInventoryFull)
                .setDefaultValue(true)
                .setSaveConsumer(value -> settings.skipWhenInventoryFull = value)
                .build());
        safety.addEntry(entries.startBooleanToggle(Component.literal("聊天回饋"), settings.chatFeedback)
                .setDefaultValue(true)
                .setSaveConsumer(value -> settings.chatFeedback = value)
                .build());
        safety.addEntry(entries.startBooleanToggle(Component.literal("Action bar 摘要"), settings.actionBarSummary)
                .setDefaultValue(true)
                .setSaveConsumer(value -> settings.actionBarSummary = value)
                .build());
        safety.addEntry(entries.startBooleanToggle(Component.literal("公告已送出購買"), settings.announcePurchases)
                .setDefaultValue(true)
                .setSaveConsumer(value -> settings.announcePurchases = value)
                .build());
        safety.addEntry(entries.startBooleanToggle(Component.literal("Debug 日誌"), settings.debugLog)
                .setDefaultValue(false)
                .setSaveConsumer(value -> settings.debugLog = value)
                .build());

        ConfigCategory printer = builder.getOrCreateCategory(Component.literal("Printer / 材料"));
        printer.addEntry(entries.startEnumSelector(Component.literal("Printer gate"),
                        AutoBuyConfig.PrinterGateMode.class, settings.printerGateMode)
                .setEnumNameProvider(value -> Component.literal(switch ((AutoBuyConfig.PrinterGateMode) value) {
                    case ALWAYS -> "不檢查 Printer";
                    case PRINTER_ENABLED -> "Work Switch＋Print";
                    case PRINTER_PRINTING -> "正在放置";
                }))
                .setDefaultValue(AutoBuyConfig.PrinterGateMode.PRINTER_ENABLED)
                .setSaveConsumer(value -> settings.printerGateMode = value)
                .build());
        printer.addEntry(entries.startEnumSelector(Component.literal("需求計數"),
                        AutoBuyConfig.RequiredCountMode.class, settings.requiredCountMode)
                .setEnumNameProvider(value -> Component.literal(switch ((AutoBuyConfig.RequiredCountMode) value) {
                    case MISSING -> "尚未放置 (countMissing)";
                    case TOTAL -> "整份需求 (countTotal)";
                }))
                .setDefaultValue(AutoBuyConfig.RequiredCountMode.MISSING)
                .setTooltip(Component.literal("精確單層固定使用未放置數（countMissing − countMismatched），不受此設定影響；大量模式使用此設定。"))
                .setSaveConsumer(value -> settings.requiredCountMode = value)
                .build());

        ConfigCategory lists = builder.getOrCreateCategory(Component.literal("白名單 / 錯誤樣式"));
        lists.addEntry(entries.startBooleanToggle(Component.literal("自適應白名單（最近藍圖）"), settings.autoSyncWhitelist)
                .setDefaultValue(false)
                .setTooltip(Component.literal("依玩家到已啟用藍圖區域的距離自動選擇補料目標，完整替換白名單並移除其他選區物品。"),
                        Component.literal("精確單層只保留本層未放置材料；大量模式使用整份藍圖缺料。關閉自動補料仍會同步，但不購買。"))
                .setSaveConsumer(value -> settings.autoSyncWhitelist = value)
                .build());
        lists.addEntry(entries.startStrList(Component.literal("白名單物品 ID"), new ArrayList<>(settings.whitelist))
                .setExpanded(false)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.literal("空白白名單＝全部禁止；minecraft: 前綴會正規化移除。啟用自適應後，此清單會被最近藍圖的材料取代。"))
                .setSaveConsumer(value -> settings.whitelist = new ArrayList<>(value))
                .build());
        lists.addEntry(entries.startStrList(Component.literal("失敗訊息樣式"),
                        new ArrayList<>(settings.failurePatterns))
                .setExpanded(false)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.literal("伺服器購買失敗訊息，子字串比對；不用區分大小寫。"))
                .setSaveConsumer(value -> settings.failurePatterns = new ArrayList<>(value))
                .build());

        return builder.build();
    }
}

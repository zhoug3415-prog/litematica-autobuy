package dev.autobuy.litematica;

import com.google.common.collect.Multimap;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.scheduler.ITask;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.BlockInfoListType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public final class LitematicaBridge {
    private static final Logger LOG = AutoBuyClient.LOGGER;
    private static final String PRINTER_MOD_ID = "litematica-printer";
    private static final String CONFIG_UTILS = "me.aleksilassila.litematica.printer.utils.ConfigUtils";
    private static final String MODULE_MANAGER = "me.aleksilassila.litematica.printer.handler.ModuleManager";
    private static final String TASK_COUNT_BLOCKS_PLACEMENT =
            "fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement";

    private String lastError;
    private boolean materialListProgressWarningLogged;
    private boolean printerReflectionInitialized;
    private boolean printerApiAvailable;
    private boolean printerWarningLogged;
    private Method isPrinterEnable;
    private Method isPrintEnabled;
    private Method getPrinterStatus;

    public Optional<SchematicPlacement> getSelectedPlacement() {
        try {
            var manager = DataManager.getSchematicPlacementManager();
            lastError = null;
            return manager == null
                    ? Optional.empty()
                    : Optional.ofNullable(manager.getSelectedSchematicPlacement());
        } catch (Exception | LinkageError e) {
            fail("讀取 Litematica 選取 placement 失敗", e);
            return Optional.empty();
        }
    }

    public MaterialListBase getMaterialList(SchematicPlacement placement) {
        try {
            if (placement == null) {
                return null;
            }
            MaterialListBase list = placement.getMaterialList();
            lastError = null;
            return list;
        } catch (Exception | LinkageError e) {
            fail("讀取 Litematica 材料清單失敗", e);
            return null;
        }
    }

    public PrinterState getPrinterState(AutoBuyConfig.PrinterGateMode mode) {
        if (mode == AutoBuyConfig.PrinterGateMode.ALWAYS) {
            return PrinterState.ACTIVE;
        }
        if (!FabricLoader.getInstance().isModLoaded(PRINTER_MOD_ID)) {
            return PrinterState.NOT_INSTALLED;
        }

        initPrinterReflection();
        if (!printerApiAvailable || isPrinterEnable == null || isPrintEnabled == null) {
            return PrinterState.UNAVAILABLE;
        }
        if (mode == AutoBuyConfig.PrinterGateMode.PRINTER_PRINTING && getPrinterStatus == null) {
            return PrinterState.UNAVAILABLE;
        }

        try {
            boolean workSwitch = (Boolean) isPrinterEnable.invoke(null);
            boolean printEnabled = (Boolean) isPrintEnabled.invoke(null);
            if (!workSwitch || !printEnabled) {
                return PrinterState.DISABLED;
            }
            if (mode == AutoBuyConfig.PrinterGateMode.PRINTER_ENABLED) {
                return PrinterState.ENABLED;
            }

            Object status = getPrinterStatus == null ? null : getPrinterStatus.invoke(null);
            if (status instanceof Enum<?> printerStatus && "PRINTING".equals(printerStatus.name())) {
                return PrinterState.PRINTING;
            }
            return PrinterState.IDLE;
        } catch (Exception | LinkageError e) {
            markPrinterApiUnavailable("讀取 Printer 狀態失敗", e);
            return PrinterState.UNAVAILABLE;
        }
    }

    public MaterialListProgress getMaterialListProgress() {
        try {
            Optional<SchematicPlacement> selectedOptional = getSelectedPlacement();
            if (selectedOptional.isEmpty()) {
                return MaterialListProgress.unknown();
            }
            SchematicPlacement selected = selectedOptional.get();

            for (Object task : TaskScheduler.getInstanceClient().getAllTasks()) {
                if (!TASK_COUNT_BLOCKS_PLACEMENT.equals(task.getClass().getName())) {
                    continue;
                }
                Field placementField = findField(task.getClass(), "schematicPlacement");
                if (placementField == null) {
                    continue;
                }
                Object taskPlacement = placementField.get(task);
                if (!(taskPlacement instanceof SchematicPlacement placement)) {
                    continue;
                }
                if (placement != selected && !placement.getName().equals(selected.getName())) {
                    continue;
                }

                Object pending = findFieldValue(task, "pendingChunks");
                Object boxes = findFieldValue(task, "boxesInChunks");
                if (!(pending instanceof List<?> pendingList) || !(boxes instanceof Multimap<?, ?> multimap)) {
                    continue;
                }
                int total = multimap.keySet().size();
                int remaining = pendingList.size();
                if (total > 0) {
                    return new MaterialListProgress(total, remaining);
                }
            }
        } catch (Exception | LinkageError e) {
            if (!materialListProgressWarningLogged) {
                materialListProgressWarningLogged = true;
                LOG.warn("[AutoBuy] Unable to read Litematica material list progress; ETA will be unavailable.", e);
            }
        }
        return MaterialListProgress.unknown();
    }

    public boolean switchMaterialListType(boolean renderLayers) {
        try {
            Class<?> taskClass = Class.forName(TASK_COUNT_BLOCKS_PLACEMENT);
            if (TaskScheduler.getInstanceClient().hasTask(taskClass.asSubclass(ITask.class))) {
                return false;
            }

            Optional<SchematicPlacement> placementOptional = getSelectedPlacement();
            if (placementOptional.isEmpty()) {
                return false;
            }
            MaterialListBase list = getMaterialList(placementOptional.get());
            if (list == null) {
                return false;
            }

            BlockInfoListType desired = renderLayers
                    ? BlockInfoListType.RENDER_LAYERS : BlockInfoListType.ALL;
            if (list.getMaterialListType() == desired) {
                return true;
            }

            list.setMaterialListType(desired);
            list.setMaterialListEntries(List.of());
            list.reCreateMaterialList();
            LOG.info("[AutoBuy] Switched Litematica material list type to {}", desired.name());
            return true;
        } catch (Exception | LinkageError e) {
            LOG.error("[AutoBuy] Failed to switch Litematica material list type", e);
            return false;
        }
    }

    public String getRenderLayerModeName() {
        try {
            var range = DataManager.getRenderLayerRange();
            if (range == null || range.getLayerMode() == null) {
                return "";
            }
            return switch (range.getLayerMode().name()) {
                case "SINGLE" -> "單層";
                case "ALL_BELOW" -> "下方所有";
                case "ALL_ABOVE" -> "上方所有";
                case "ALL" -> "全部";
                default -> range.getLayerMode().name();
            };
        } catch (Exception | LinkageError e) {
            return "";
        }
    }

    private static Object findFieldValue(Object target, String name) throws IllegalAccessException {
        Field field = findField(target.getClass(), name);
        return field == null ? null : field.get(target);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    public record MaterialListProgress(int totalChunks, int remainingChunks) {
        public static MaterialListProgress unknown() {
            return new MaterialListProgress(0, 0);
        }

        public boolean known() {
            return totalChunks > 0 && remainingChunks >= 0;
        }
    }

    private void initPrinterReflection() {
        if (printerReflectionInitialized) {
            return;
        }
        printerReflectionInitialized = true;

        try {
            Class<?> configUtils = Class.forName(CONFIG_UTILS);
            isPrinterEnable = configUtils.getMethod("isPrinterEnable");
            isPrintEnabled = configUtils.getMethod("isPrintEnabled");

            try {
                Class<?> moduleManager = Class.forName(MODULE_MANAGER);
                getPrinterStatus = moduleManager.getMethod("getPrinterStatus");
            } catch (ReflectiveOperationException | LinkageError e) {
                getPrinterStatus = null;
                LOG.warn("[AutoBuy] Printer status API not found; PRINTER_PRINTING mode is unavailable.");
            }

            printerApiAvailable = true;
            LOG.info("[AutoBuy] Hooked into Litematica Printer API.");
        } catch (ReflectiveOperationException | LinkageError e) {
            markPrinterApiUnavailable("Litematica Printer API 不相容", e);
        }
    }

    private void markPrinterApiUnavailable(String message, Throwable throwable) {
        printerApiAvailable = false;
        if (!printerWarningLogged) {
            printerWarningLogged = true;
            LOG.error("[AutoBuy] {}; printer-gated modes will stop safely.", message, throwable);
        }
    }

    private void fail(String message, Throwable throwable) {
        lastError = message + ": " + (throwable == null ? "unknown" : throwable.getMessage());
        LOG.error("[AutoBuy] {}", message, throwable);
    }

    public String getLastError() {
        return lastError;
    }

    public enum PrinterState {
        ACTIVE,
        ENABLED,
        PRINTING,
        IDLE,
        DISABLED,
        NOT_INSTALLED,
        UNAVAILABLE
    }
}

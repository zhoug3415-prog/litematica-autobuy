package dev.autobuy.litematica;

import com.google.common.collect.Multimap;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.IMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.BlockInfoListType;
import net.minecraft.client.Minecraft;
import fi.dy.masa.malilib.util.LayerMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LitematicaBridge {
    private static final Logger LOG = AutoBuyClient.LOGGER;
    private static final String PRINTER_MOD_ID = "litematica-printer";
    private static final String CONFIG_UTILS = "me.aleksilassila.litematica.printer.utils.ConfigUtils";
    private static final String MODULE_MANAGER = "me.aleksilassila.litematica.printer.handler.ModuleManager";
    private static final String TASK_COUNT_BLOCKS_PLACEMENT =
            "fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement";

    private String lastError;
    private MaterialCount materialCount;
    private boolean adaptiveWhitelist;
    private SchematicPlacement nearestPlacement;
    private MaterialListBase bulkRecountRequired;
    private boolean materialListProgressWarningLogged;
    private boolean printerReflectionInitialized;
    private boolean printerApiAvailable;
    private boolean printerWarningLogged;
    private Method isPrinterEnable;
    private Method isPrintEnabled;
    private Method getPrinterStatus;

    public void setAdaptiveWhitelist(boolean enabled) {
        if (adaptiveWhitelist != enabled) {
            if (!enabled && materialCount != null && !materialCount.selection.singleLayer()) {
                bulkRecountRequired = materialCount.materialList;
            }
            invalidateMaterialCount();
            nearestPlacement = null;
        }
        adaptiveWhitelist = enabled;
    }

    public boolean isAdaptiveWhitelist() {
        return adaptiveWhitelist;
    }

    public Optional<SchematicPlacement> getTargetPlacement() {
        try {
            var manager = DataManager.getSchematicPlacementManager();
            lastError = null;
            if (manager == null) {
                return Optional.empty();
            }
            if (!adaptiveWhitelist) {
                return Optional.ofNullable(manager.getSelectedSchematicPlacement());
            }
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return Optional.empty();
            }
            SchematicPlacement closest = null;
            SchematicPlacement selected = manager.getSelectedSchematicPlacement();
            double closestDistance = Double.POSITIVE_INFINITY;
            for (SchematicPlacement placement : manager.getAllSchematicsPlacements()) {
                if (!placement.isEnabled()) {
                    continue;
                }
                double distance = Double.POSITIVE_INFINITY;
                for (var box : placement.getSubRegionBoxes(
                        SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values()) {
                    BlockPos first = box.getPos1();
                    BlockPos second = box.getPos2();
                    if (first == null || second == null) {
                        continue;
                    }
                    double dx = distanceToInterval(player.getX(), first.getX(), second.getX());
                    double dy = distanceToInterval(player.getY(), first.getY(), second.getY());
                    double dz = distanceToInterval(player.getZ(), first.getZ(), second.getZ());
                    distance = Math.min(distance, dx * dx + dy * dy + dz * dz);
                }
                if (Double.isFinite(distance) && (distance < closestDistance
                        || (distance == closestDistance && (placement == nearestPlacement
                        || (closest != nearestPlacement && placement == selected))))) {
                    closest = placement;
                    closestDistance = distance;
                }
            }
            nearestPlacement = closest;
            return Optional.ofNullable(closest);
        } catch (Exception | LinkageError e) {
            fail("讀取 Litematica 目標 placement 失敗", e);
            return Optional.empty();
        }
    }

    private static double distanceToInterval(double position, int first, int second) {
        return Math.max(0.0, Math.max(Math.min(first, second) - position,
                position - ((double) Math.max(first, second) + 1.0)));
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
            Optional<SchematicPlacement> selectedOptional = getTargetPlacement();
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
                if (placement != selected) {
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

    public MaterialSelection getMaterialSelection(boolean singleLayer) {
        var range = DataManager.getRenderLayerRange();
        if (singleLayer && range.getLayerMode() != LayerMode.SINGLE_LAYER) {
            return null;
        }
        return getTargetPlacement().filter(SchematicPlacement::isEnabled).map(placement -> {
            Map<String, PlacementTransform> subRegions = new HashMap<>();
            for (var region : placement.getAllSubRegionsPlacements()) {
                subRegions.put(region.getName(), new PlacementTransform(region.getPos().immutable(),
                        region.getRotation(), region.getMirror(), region.isEnabled()));
            }
            return new MaterialSelection(placement, singleLayer,
                    singleLayer ? range.getAxis() : null, singleLayer ? range.getLayerSingle() : 0,
                    new PlacementTransform(placement.getOrigin().immutable(), placement.getRotation(),
                            placement.getMirror(), placement.isEnabled()), Map.copyOf(subRegions));
        }).orElse(null);
    }

    public boolean isCurrentSelection(MaterialSelection selection) {
        return selection != null && selection.equals(getMaterialSelection(selection.singleLayer()));
    }

    public void invalidateChangedSelection() {
        if (materialCount != null && !isCurrentSelection(materialCount.selection)) {
            materialCount.valid = false;
        }
        cancelObsoleteCount();
    }

    public void invalidateMaterialCount() {
        if (materialCount != null) {
            materialCount.valid = false;
        }
        cancelObsoleteCount();
    }

    private void cancelObsoleteCount() {
        if (materialCount != null && !materialCount.valid && materialCount.entries == null
                && TaskScheduler.getInstanceClient().getAllTasks().contains(materialCount.task)) {
            TaskScheduler.getInstanceClient().removeTask(materialCount.task);
        }
    }

    public boolean prepareCachedBulkList(MaterialListBase list) {
        if (list != bulkRecountRequired) {
            return true;
        }
        if (TaskScheduler.getInstanceClient().hasTask(TaskCountBlocksPlacement.class)) {
            return false;
        }
        bulkRecountRequired = null;
        materialCount = null;
        list.setMaterialListType(BlockInfoListType.ALL);
        list.setMaterialListEntries(List.of());
        list.reCreateMaterialList();
        return false;
    }

    public void resetMaterialTracking() {
        materialCount = null;
        nearestPlacement = null;
        bulkRecountRequired = null;
    }

    public boolean prepareMaterialList(MaterialListBase list, boolean singleLayer) {
        invalidateChangedSelection();
        if (materialCount != null) {
            if (materialCount.entries != null) {
                MaterialCount completed = materialCount;
                materialCount = null;
                if (completed.valid && completed.materialList == list
                        && completed.selection.singleLayer() == singleLayer
                        && list.getMaterialListType() == completed.getMaterialListType()) {
                    list.setMaterialListEntries(completed.entries);
                    return true;
                }
            } else if (TaskScheduler.getInstanceClient().hasTask(TaskCountBlocksPlacement.class)) {
                if (materialCount.selection.singleLayer() != singleLayer) {
                    materialCount.valid = false;
                }
                return false;
            } else {
                materialCount = null;
            }
        }

        if (!TaskScheduler.getInstanceClient().hasTask(TaskCountBlocksPlacement.class)) {
            MaterialSelection selection = getMaterialSelection(singleLayer);
            if (selection != null) {
                startMaterialCount(selection, list);
            }
        }
        return false;
    }

    private void startMaterialCount(MaterialSelection selection, MaterialListBase list) {
        MaterialCount count = new MaterialCount(selection, list);
        list.setMaterialListType(count.getMaterialListType());
        list.setMaterialListEntries(List.of());
        TaskCountBlocksPlacement task = new TaskCountBlocksPlacement(selection.placement(), count);
        count.task = task;
        task.disableCompletionMessage();
        TaskScheduler.getInstanceClient().scheduleTask(task, 20);
        materialCount = count;
    }

    public boolean canResumeMaterialScan() {
        return (materialCount != null && materialCount.entries != null)
                || !TaskScheduler.getInstanceClient().hasTask(TaskCountBlocksPlacement.class);
    }

    public record MaterialSelection(SchematicPlacement placement, boolean singleLayer,
                                    Direction.Axis axis, int layer, PlacementTransform transform,
                                    Map<String, PlacementTransform> subRegions) {
    }

    public record PlacementTransform(BlockPos position, Rotation rotation, Mirror mirror, boolean enabled) {
    }

    private static final class MaterialCount implements IMaterialList {
        private final MaterialSelection selection;
        private final MaterialListBase materialList;
        private TaskCountBlocksPlacement task;
        private List<MaterialListEntry> entries;
        private boolean valid = true;

        private MaterialCount(MaterialSelection selection, MaterialListBase materialList) {
            this.selection = selection;
            this.materialList = materialList;
        }

        @Override
        public BlockInfoListType getMaterialListType() {
            return selection.singleLayer() ? BlockInfoListType.RENDER_LAYERS : BlockInfoListType.ALL;
        }

        @Override
        public void setMaterialListEntries(List<MaterialListEntry> entries) {
            this.entries = List.copyOf(entries);
        }
    }

    public boolean switchMaterialListType(boolean renderLayers) {
        try {
            if (TaskScheduler.getInstanceClient().hasTask(TaskCountBlocksPlacement.class)) {
                return false;
            }

            if (renderLayers && getMaterialSelection(true) == null) {
                return false;
            }
            Optional<SchematicPlacement> placementOptional = getTargetPlacement();
            if (placementOptional.isEmpty()) {
                return false;
            }
            MaterialListBase list = getMaterialList(placementOptional.get());
            if (list == null) {
                return false;
            }

            if (renderLayers || adaptiveWhitelist) {
                MaterialSelection selection = getMaterialSelection(renderLayers);
                if (selection == null) {
                    return false;
                }
                startMaterialCount(selection, list);
                return true;
            }
            resetMaterialTracking();
            BlockInfoListType desired = BlockInfoListType.ALL;
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
                case "SINGLE_LAYER" -> "單層 " + range.getAxis().name() + "=" + range.getLayerSingle();
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

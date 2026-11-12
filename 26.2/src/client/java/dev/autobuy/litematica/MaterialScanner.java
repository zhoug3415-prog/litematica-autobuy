package dev.autobuy.litematica;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.BlockInfoListType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MaterialScanner {
    private final LitematicaBridge bridge;

    public MaterialScanner(LitematicaBridge bridge) {
        this.bridge = bridge;
    }

    public ScanResult scan(AutoBuyConfig.PurchaseMode purchaseMode, AutoBuyConfig.RequiredCountMode mode) {
        Optional<SchematicPlacement> placementOptional = bridge.getTargetPlacement();
        if (placementOptional.isEmpty()) {
            String error = bridge.getLastError();
            return error != null ? ScanResult.error(error) : ScanResult.noPlacement();
        }

        SchematicPlacement placement = placementOptional.get();
        boolean singleLayerOnly = purchaseMode == AutoBuyConfig.PurchaseMode.EXACT_LAYER;
        try {
            if (singleLayerOnly && bridge.getMaterialSelection(true) == null) {
                return ScanResult.singleLayerRequired(placement.getName());
            }
            MaterialListBase list = bridge.getMaterialList(placement);
            if (list == null) {
                return ScanResult.error(bridge.getLastError() != null
                        ? bridge.getLastError() : "material list unavailable");
            }

            boolean freshCount = singleLayerOnly || bridge.isAdaptiveWhitelist();
            if (freshCount && !bridge.prepareMaterialList(list, singleLayerOnly)) {
                return ScanResult.listEmpty(placement.getName());
            }
            if (!freshCount && !bridge.prepareCachedBulkList(list)) {
                return ScanResult.listEmpty(placement.getName());
            }
            if (!freshCount && list.getMaterialListType() != BlockInfoListType.ALL) {
                bridge.switchMaterialListType(false);
                return ScanResult.listEmpty(placement.getName());
            }
            var entries = list.getMaterialsAll();
            if (!freshCount && entries.isEmpty()) {
                return ScanResult.listEmpty(placement.getName());
            }

            Map<String, Integer> required = new LinkedHashMap<>();
            Map<String, Integer> missingByItem = new LinkedHashMap<>();
            Map<String, Integer> maxStackByItem = new LinkedHashMap<>();
            for (MaterialListEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                ItemStack stack = entry.getStack();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (key == null) {
                    continue;
                }
                String id = ItemIds.normalizeRegistryId(key.toString());
                int missing = Math.max(0, entry.getCountMissing());
                if (singleLayerOnly) {
                    // Litematica includes non-air mismatches in countMissing.
                    missing = Math.max(0, missing - Math.max(0, entry.getCountMismatched()));
                }
                int total = Math.max(0, entry.getCountTotal());
                int maxStack = Math.max(1, stack.getMaxStackSize());

                maxStackByItem.merge(id, maxStack, Math::max);
                missingByItem.merge(id, missing, (left, right) ->
                        (int) Math.min(Integer.MAX_VALUE, (long) left + right));

                int count = !singleLayerOnly && mode == AutoBuyConfig.RequiredCountMode.TOTAL
                        ? total : missing;
                if (count <= 0) {
                    continue;
                }
                required.merge(id, count, (left, right) ->
                        (int) Math.min(Integer.MAX_VALUE, (long) left + right));
            }

            boolean renderLayersOnly = list.getMaterialListType() == BlockInfoListType.RENDER_LAYERS;
            return ScanResult.ok(placement.getName(), Map.copyOf(required),
                    Map.copyOf(missingByItem), Map.copyOf(maxStackByItem), renderLayersOnly);
        } catch (Exception | LinkageError e) {
            AutoBuyClient.LOGGER.error("[AutoBuy] Material scan failed", e);
            return ScanResult.error("材料掃描失敗：" + e.getMessage());
        }
    }

    public enum Status {
        OK,
        NO_PLACEMENT,
        SINGLE_LAYER_REQUIRED,
        LIST_EMPTY,
        ERROR
    }

    public record ScanResult(Status status, String placementName, Map<String, Integer> required,
                             Map<String, Integer> missingByItem, Map<String, Integer> maxStackByItem,
                             boolean renderLayersOnly, String error) {
        public static ScanResult ok(String name, Map<String, Integer> required,
                                    Map<String, Integer> missingByItem,
                                    Map<String, Integer> maxStackByItem,
                                    boolean renderLayersOnly) {
            return new ScanResult(Status.OK, name, required, missingByItem, maxStackByItem,
                    renderLayersOnly, null);
        }

        public static ScanResult noPlacement() {
            return new ScanResult(Status.NO_PLACEMENT, null, Map.of(), Map.of(), Map.of(),
                    false, null);
        }

        public static ScanResult singleLayerRequired(String name) {
            return new ScanResult(Status.SINGLE_LAYER_REQUIRED, name, Map.of(), Map.of(), Map.of(),
                    false, null);
        }

        public static ScanResult listEmpty(String name) {
            return new ScanResult(Status.LIST_EMPTY, name, Map.of(), Map.of(), Map.of(),
                    false, null);
        }

        public static ScanResult error(String message) {
            return new ScanResult(Status.ERROR, null, Map.of(), Map.of(), Map.of(), false, message);
        }
    }
}

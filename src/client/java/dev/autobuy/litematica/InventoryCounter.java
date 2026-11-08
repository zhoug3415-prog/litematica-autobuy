package dev.autobuy.litematica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class InventoryCounter {
    public CountResult count(Minecraft mc, boolean includeOffhand) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return new CountResult(Map.of(), 0, Map.of(), 0);
        }

        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> partialRoom = new HashMap<>();
        var mainItems = player.getInventory().getNonEquipmentItems();
        int emptySlots = 0;

        for (ItemStack stack : mainItems) {
            if (stack == null || stack.isEmpty()) {
                emptySlots++;
                continue;
            }
            add(counts, stack);
            addPartialRoom(partialRoom, stack);
        }

        if (includeOffhand) {
            add(counts, player.getOffhandItem());
        }

        return new CountResult(Map.copyOf(counts), emptySlots, Map.copyOf(partialRoom),
                mainItems.size());
    }

    private static void add(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return;
        }
        String id = ItemIds.normalizeRegistryId(key.toString());
        counts.merge(id, stack.getCount(), (left, right) ->
                (int) Math.min(Integer.MAX_VALUE, (long) left + right));
    }

    private static void addPartialRoom(Map<String, Integer> partialRoom, ItemStack stack) {
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return;
        }
        int room = stack.getMaxStackSize() - stack.getCount();
        if (room <= 0) {
            return;
        }
        String id = ItemIds.normalizeRegistryId(key.toString());
        partialRoom.merge(id, room, (left, right) ->
                (int) Math.min(Integer.MAX_VALUE, (long) left + right));
    }

    public record CountResult(Map<String, Integer> counts, int emptySlots,
                              Map<String, Integer> partialRoom, int mainSlotCount) {
    }
}

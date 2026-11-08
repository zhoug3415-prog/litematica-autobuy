package dev.autobuy.litematica;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoBuyClient implements ClientModInitializer {
    public static final String MOD_ID = "litematica-autobuy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AutoBuyManager manager;
    private static KeyMapping toggleKey;
    private static KeyMapping scanKey;
    private static KeyMapping reloadKey;
    private static KeyMapping syncWhitelistKey;
    private static KeyMapping purchaseModeKey;

    @Override
    public void onInitializeClient() {
        AutoBuyConfig config = AutoBuyConfig.loadOrCreate();
        manager = new AutoBuyManager(config);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = register("toggle", category);
        scanKey = register("scan", category);
        reloadKey = register("reload", category);
        syncWhitelistKey = register("sync_whitelist", category);
        purchaseModeKey = register("purchase_mode", category);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (manager == null) {
                return;
            }
            while (toggleKey.consumeClick()) {
                manager.requestToggle();
            }
            while (scanKey.consumeClick()) {
                manager.requestScan();
            }
            while (reloadKey.consumeClick()) {
                manager.requestReload();
            }
            while (syncWhitelistKey.consumeClick()) {
                manager.requestWhitelistSync();
            }
            while (purchaseModeKey.consumeClick()) {
                manager.requestPurchaseModeToggle();
            }
            manager.onClientTick(client);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (manager != null) {
                manager.onGameMessage(message.getString());
            }
        });

        if (config.loadWarning != null) {
            LOGGER.warn(config.loadWarning);
        }
        LOGGER.info("Litematica AutoBuy initialized; hotkeys are unbound by default.");
    }

    private static KeyMapping register(String name, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + "." + name,
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                category
        ));
    }
}

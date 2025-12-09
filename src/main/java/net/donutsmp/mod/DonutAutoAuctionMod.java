package net.donutsmp.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DonutAutoAuctionMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("donut-auto-auction");
    
    private static KeyBinding autoAuctionKey;
    private static KeybindHandler keybindHandler;
    private static ConfigManager configManager;
    private static AuctionExecutor auctionExecutor;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Donut Auto-Auction Mod");
        
        // Initialize config
        configManager = new ConfigManager();
        configManager.loadConfig();
        
        // Register keybinding
        autoAuctionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.donut.auto_auction",
            InputUtil.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_1,
            "category.donut"
        ));
        
        // Initialize handlers
        keybindHandler = new KeybindHandler(configManager);
        auctionExecutor = new AuctionExecutor(configManager);
        
        // Register client tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && configManager.isEnabled()) {
                keybindHandler.handleKeybind(client, autoAuctionKey, auctionExecutor);
            }
        });
        
        LOGGER.info("Donut Auto-Auction Mod initialized successfully");
    }
}

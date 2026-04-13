package com.flowpvp.client;

import com.flowpvp.FlowPvPMod;
import com.flowpvp.client.command.FlowRankCommand;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.StatsCache;
import com.flowpvp.client.hud.FlowPvPHud;
import com.flowpvp.client.screen.HudConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//? if >=1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;*/
//?}
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.flowpvp.client.feature.PlayerStatsScanner;
import com.flowpvp.client.screen.LeaderboardScreen;


public class FlowPvPClient implements ClientModInitializer {

    public static StatsCache STATS_CACHE;

    public static KeyBinding TOGGLE_HUD_KEY;
    public static KeyBinding OPEN_CONFIG_KEY;
    public static KeyBinding OPEN_LEADERBOARD_KEY;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        STATS_CACHE = new StatsCache();

        // Register keybindings
        //? if >=1.21.9 {
        /*KeyBinding.Category flowtiersCat = KeyBinding.Category.create(net.minecraft.util.Identifier.of("flowtiers", "categories"));*/
        //?}

        TOGGLE_HUD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.flowtiers.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                //? if >=1.21.9 {
                /*flowtiersCat*/
                //?} else {
                "key.categories.flowtiers"
                //?}
        ));

        OPEN_CONFIG_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.flowtiers.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                //? if >=1.21.9 {
                /*flowtiersCat*/
                //?} else {
                "key.categories.flowtiers"
                //?}
        ));

        OPEN_LEADERBOARD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.flowtiers.open_leaderboard",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                //? if >=1.21.9 {
                /*flowtiersCat*/
                //?} else {
                "key.categories.flowtiers"
                //?}
        ));

        // Register HUD renderer
        FlowPvPHud hud = new FlowPvPHud();
        //? if >=1.21.9 {
        /*HudElementRegistry.addLast(net.minecraft.util.Identifier.of("flowtiers", "hud"), hud::render);*/
        //?} else {
        HudRenderCallback.EVENT.register(hud::render);
        //?}

        // Register client commands
        FlowRankCommand.register();

        // Handle keybind presses each tick
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_HUD_KEY.wasPressed()) {
                ModConfig.INSTANCE.hudEnabled = !ModConfig.INSTANCE.hudEnabled;
                ModConfig.save();
                FlowPvPMod.LOGGER.info("[FlowTiers] HUD {}", ModConfig.INSTANCE.hudEnabled ? "enabled" : "disabled");
            }
            while (OPEN_CONFIG_KEY.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new HudConfigScreen(hud));
                }
            }
            while (OPEN_LEADERBOARD_KEY.wasPressed()) {
    client.setScreen(new LeaderboardScreen());
}
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModConfig.save();
            STATS_CACHE.shutdown();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
    PlayerStatsScanner.tick();
});

    

    }
}

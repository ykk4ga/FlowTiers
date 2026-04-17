package com.flowpvp.client;

import com.flowpvp.FlowPvPMod;
import com.flowpvp.client.command.FlowRankCommand;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.StatsCache;
import com.flowpvp.client.feature.PlayerStatsScanner;
import com.flowpvp.client.feature.UpdateChecker;
import com.flowpvp.client.hud.FlowPvPHud;
import com.flowpvp.client.screen.HudConfigScreen;
import com.flowpvp.client.screen.LeaderboardScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//? if >=1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;*/
//?}
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class FlowPvPClient implements ClientModInitializer {

    public static StatsCache STATS_CACHE;

    public static KeyBinding TOGGLE_HUD_KEY;
    public static KeyBinding OPEN_CONFIG_KEY;
    public static KeyBinding OPEN_LEADERBOARD_KEY;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        STATS_CACHE = new StatsCache();

        // Kick off update check immediately in the background
        UpdateChecker.checkAsync();

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

        // Reset per-session notification state each time the player joins a world/server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                UpdateChecker.resetNotification());

        // Handle keybind presses each tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
            sendUpdateNotificationIfReady(client);
        });
    }

    // -------------------------------------------------------------------------
    // Update notification
    // -------------------------------------------------------------------------

    private static void sendUpdateNotificationIfReady(net.minecraft.client.MinecraftClient client) {
        if (UpdateChecker.notificationSent) return;
        if (client.player == null) return;
        if (!UpdateChecker.checked) return;

        if (!UpdateChecker.updateAvailable) {
            // No update — mark as handled so we don't keep checking
            UpdateChecker.notificationSent = true;
            return;
        }

        // Wait ~3 seconds (60 ticks) after the player loads in before showing the message,
        // so it doesn't get lost in join noise
        if (++UpdateChecker.notifyDelayTicks < 60) return;

        String current = UpdateChecker.getDisplayVersion();
        String latest  = UpdateChecker.latestVersion;
        String url     = UpdateChecker.updateUrl;

        // Line 1: header + versions
        Text line1 = Text.empty()
                .append(Text.literal("[FlowTiers] ").setStyle(Style.EMPTY.withColor(0x00BFFF).withBold(true)))
                .append(Text.literal("\u2B06 Update available! ").setStyle(Style.EMPTY.withColor(0xFFD700)))
                .append(Text.literal("(").setStyle(Style.EMPTY.withColor(0xAAAAAA)))
                .append(Text.literal("v" + current).setStyle(Style.EMPTY.withColor(0xFFFFFF)))
                .append(Text.literal(" \u2192 ").setStyle(Style.EMPTY.withColor(0xAAAAAA)))
                .append(Text.literal("v" + latest).setStyle(Style.EMPTY.withColor(0x55FF55)))
                .append(Text.literal(")").setStyle(Style.EMPTY.withColor(0xAAAAAA)));

        // Line 2: download URL
        Text line2 = Text.empty()
                .append(Text.literal("[FlowTiers] ").setStyle(Style.EMPTY.withColor(0x00BFFF).withBold(true)))
                .append(Text.literal("Download: ").setStyle(Style.EMPTY.withColor(0xAAAAAA)))
                .append(Text.literal(url).setStyle(Style.EMPTY.withColor(0x00BFFF).withUnderline(true)));

        client.player.sendMessage(line1, false);
        client.player.sendMessage(line2, false);

        UpdateChecker.notificationSent = true;
    }
}

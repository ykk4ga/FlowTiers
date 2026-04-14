package com.flowpvp.client.feature;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.data.PlayerStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStatsScanner {

    // Cache for fetched stats
    private static final Map<UUID, PlayerStats> CACHE = new HashMap<>();

    // Cooldown tracking (prevents API spam)
    private static final Map<UUID, Long> LAST_FETCH = new HashMap<>();

    // 60 seconds cooldown per player
    private static final long FETCH_COOLDOWN_MS = 60_000;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null) return;

        long now = System.currentTimeMillis();

        // --- Visible world players (immediate fetch, shows in nametags) ---
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();

            if (LAST_FETCH.containsKey(uuid) &&
                now - LAST_FETCH.get(uuid) < FETCH_COOLDOWN_MS) {
                continue;
            }

            LAST_FETCH.put(uuid, now);

            FlowPvPClient.STATS_CACHE.getStatsByUuid(uuid)
                .thenAcceptAsync(stats -> {
                    mc.execute(() -> CACHE.put(uuid, stats));
                })
                .exceptionally(ex -> null);
        }

        // --- All server players (scheduled fetch, populates tab list) ---
        if (mc.getNetworkHandler() == null) return;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            //? if >=1.21.9 {
            /*UUID listUuid = entry.getProfile().id();*/
            //?} else {
            UUID listUuid = entry.getProfile().getId();
            //?}
            if (listUuid == null) continue;

            if (LAST_FETCH.containsKey(listUuid) &&
                now - LAST_FETCH.get(listUuid) < FETCH_COOLDOWN_MS) {
                continue;
            }

            LAST_FETCH.put(listUuid, now);
            FlowPvPClient.STATS_CACHE.scheduleStatsByUuid(listUuid);
        }
    }

    public static PlayerStats get(UUID uuid) {
        return CACHE.get(uuid);
    }
}

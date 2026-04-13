package com.flowpvp.client.feature;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.data.PlayerStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

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

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();

            // Skip if recently fetched
            if (LAST_FETCH.containsKey(uuid) &&
                now - LAST_FETCH.get(uuid) < FETCH_COOLDOWN_MS) {
                continue;
            }

            LAST_FETCH.put(uuid, now);

            // Fetch stats silently
            FlowPvPClient.STATS_CACHE.getStatsByUuid(uuid)
                .thenAcceptAsync(stats -> {
                    mc.execute(() -> {
                        CACHE.put(uuid, stats);

                        // OPTIONAL DEBUG:
                        // System.out.println("Fetched stats for " + stats.lastKnownName);
                    });
                })
                .exceptionally(ex -> {
                    System.err.println("Failed to fetch stats for " + player.getName().getString());
                    return null;
                });
        }
    }

    // Getter so you can use this anywhere else in your mod
    public static PlayerStats get(UUID uuid) {
        return CACHE.get(uuid);
    }
}
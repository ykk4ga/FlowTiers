package com.flowpvp.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

/**
 * Heuristic detection for "is the player currently in a FlowPvP ranked
 * match / duels lobby?".
 *
 * FlowPvP shows tier/ELO next to names in several ways:
 *   A. Plain numeric prefix in the display name ("1021 Steve", "464 Steve").
 *   B. Via the tab-list ("list") scoreboard slot — vanilla paints the score
 *      as a yellow number to the right of each name, AFTER our mixin returns.
 *   C. Via sidebar titles / tab header text containing keywords.
 *
 * Detection strategies (any match → treat as ranked):
 *   1. Sidebar scoreboard title keyword hit.
 *   2. Tab list ("list") slot has an active objective. This catches the case
 *      where FlowPvP paints ELO using vanilla's tab-list score rendering.
 *   3. Tab list header / footer keyword hit.
 *   4. Any visible tab entry already has tier/ELO info baked into its name
 *      (e.g. "1021 Steve", "[1234] Steve", "ELO" keyword).
 *
 * Server-level detection is cached for CACHE_MS to keep per-frame cost low.
 */
public final class RankedMatchDetector {

    private static final long CACHE_MS = 2000L;

    private static volatile boolean cachedResult = false;
    private static volatile long    cachedAt     = 0L;

    private RankedMatchDetector() {}

    public static boolean isInRankedMatch() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS) return cachedResult;

        boolean result = detect();
        cachedResult = result;
        cachedAt     = now;
        return result;
    }

    /** Returns true if the given Text already includes what looks like tier/ELO info. */
    public static boolean nameAlreadyHasTierInfo(Text text) {
        if (text == null) return false;
        String s = text.getString();
        if (s == null || s.isEmpty()) return false;

        String stripped = stripFormatCodes(s).trim();
        if (stripped.isEmpty()) return false;

        String lower = stripped.toLowerCase();

        // Explicit ELO keyword anywhere
        if (lower.contains("elo")) return true;

        // Tier badges like LT1, LT2, HT3, HT4 (with or without brackets).
        // Leaving off for now — was false-positiving on some usernames.
        // if (stripped.matches(".*\\b[LH]T[0-9]\\b.*")) return true;

        // FlowPvP duels format: plain numeric ELO prefix before the name.
        // "1021 Steve", "464  Steve", "1234 | Steve", "1234·Steve"
        // 2-5 leading digits, at least one whitespace / separator, then a
        // non-digit character so we don't catch all-digit usernames.
        if (stripped.matches("^\\d{2,5}[\\s|·•].*[A-Za-z_].*")) return true;

        // Bracketed / parenthesized ELO-looking number anywhere:
        // "[1234] Steve", "(1234) Steve", "Steve [1234]"
        // if (stripped.matches(".*[\\[(]\\s*\\d{2,5}\\s*[\\])].*")) return true;

        return false;
    }

    // -------------------------------------------------------------------------

    private static boolean detect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return false;

        // 1) Sidebar scoreboard title keyword
        // 2) Tab-list (LIST) slot has an active objective — catches FlowPvP
        //    painting ELO via vanilla score rendering (the "1021" yellow
        //    number next to names).
        try {
            Scoreboard sb = mc.world.getScoreboard();
            if (sb != null) {
                ScoreboardObjective sidebar = getObjectiveForSlot(sb, 1, "SIDEBAR");
                if (sidebar != null) {
                    String title = sidebar.getDisplayName().getString().toLowerCase();
                    if (isRankedKeyword(title)) return true;
                }
                // LIST slot = 0 in legacy, ScoreboardDisplaySlot.LIST in 1.20.4+
                if (getObjectiveForSlot(sb, 0, "LIST") != null) return true;
            }
        } catch (Throwable ignored) {
            // Reflection / API drift — fall through.
        }

        // 3) Tab list header / footer keyword
        try {
            var hud = mc.inGameHud;
            if (hud != null && hud.getPlayerListHud() != null) {
                Text header = getField(hud.getPlayerListHud(), "header", "field_2153");
                Text footer = getField(hud.getPlayerListHud(), "footer", "field_2152");
                if (textContainsRankedHint(header) || textContainsRankedHint(footer)) return true;
            }
        } catch (Throwable ignored) {}

        // 4) Scan up to 16 tab entries. If any player's display name already
        //    contains tier/ELO info, we're in a lobby that's already showing
        //    it — suppress our own append to avoid duplicating.
        try {
            ClientPlayNetworkHandler net = mc.getNetworkHandler();
            if (net != null) {
                int checked = 0;
                for (PlayerListEntry e : net.getPlayerList()) {
                    if (e == null) continue;
                    Text disp = e.getDisplayName();
                    if (disp != null && nameAlreadyHasTierInfo(disp)) return true;
                    if (++checked >= 16) break;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean textContainsRankedHint(Text t) {
        if (t == null) return false;
        return isRankedKeyword(t.getString().toLowerCase());
    }

    /** Matches a known FlowPvP ranked/duels keyword in a lowercase string. */
    private static boolean isRankedKeyword(String lower) {
        if (lower == null || lower.isEmpty()) return false;
        return lower.contains("ranked")
                || lower.contains("duel")
                || lower.contains("elo")
                || lower.contains("matchmaking")
                || lower.contains("1v1")
                || lower.contains("flowpvp")
                || lower.contains("flow pvp");
    }

    private static String stripFormatCodes(String s) {
        // Strip §x color/formatting codes so regex anchors work reliably.
        return s.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * Version-agnostic lookup for a display-slot objective.
     *  - legacySlot: integer slot id used in pre-1.20.4 Yarn (0=LIST, 1=SIDEBAR).
     *  - enumName:   simple name of the ScoreboardDisplaySlot enum constant used
     *                in 1.20.4+ ("LIST", "SIDEBAR", ...).
     */
    private static ScoreboardObjective getObjectiveForSlot(Scoreboard sb,
                                                           int legacySlot,
                                                           String enumName) {
        // Legacy signature: getObjectiveForSlot(int)
        try {
            java.lang.reflect.Method m = sb.getClass().getMethod("getObjectiveForSlot", int.class);
            Object r = m.invoke(sb, legacySlot);
            if (r instanceof ScoreboardObjective o) return o;
        } catch (Throwable ignored) {}
        // Modern signature: getObjectiveForSlot(ScoreboardDisplaySlot)
        try {
            for (java.lang.reflect.Method m : sb.getClass().getMethods()) {
                if (m.getName().equals("getObjectiveForSlot") && m.getParameterCount() == 1) {
                    Class<?> p = m.getParameterTypes()[0];
                    if (p.isEnum()) {
                        for (Object c : p.getEnumConstants()) {
                            if (c.toString().equalsIgnoreCase(enumName)) {
                                Object r = m.invoke(sb, c);
                                if (r instanceof ScoreboardObjective o) return o;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String... names) {
        for (String n : names) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(n);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
package com.flowpvp.client.data;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of a player's FlowPvP ranked stats,
 * parsed from the /api/ranked/<uuid> endpoint.
 */
public final class PlayerStats {

    /** Dashed UUID string, e.g. "036aa0bb-a468-407a-b91a-e0c51a7ed618". */
    public final String uuid;
    /** The player's last known Minecraft username. */
    public final String lastKnownName;
    /** Overall global ELO across all ladders. */
    public final int globalElo;
    /** Global leaderboard position (-1 if unranked/not on leaderboard). */
    public final int globalPosition;
    /** Per-ladder stats. Keys: SWORD, AXE, UHC, VANILLA, MACE, DIAMOND_POT, NETHERITE_OP, SMP, DIAMOND_SMP */
    public final Map<RankedLadder, LadderStats> perLadder;
    /** System.currentTimeMillis() when this entry was fetched. */
    public final long fetchedAt;

    public PlayerStats(String uuid, String lastKnownName, int globalElo, int globalPosition,
                       Map<RankedLadder, LadderStats> perLadder, long fetchedAt) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.globalElo = globalElo;
        this.globalPosition = globalPosition;
        this.perLadder = Collections.unmodifiableMap(perLadder);
        this.fetchedAt = fetchedAt;
    }

    public boolean isStale(long ttlMillis) {
        return System.currentTimeMillis() - fetchedAt > ttlMillis;
    }

    // -------------------------------------------------------------------------
    // Display-mode helpers — call these everywhere instead of reading fields directly
    // -------------------------------------------------------------------------

    /** ELO for the given display mode ("GLOBAL" or a ladder key). */
    public int getDisplayElo(RankedLadder mode) {
        if (mode == RankedLadder.GLOBAL) return globalElo;
        LadderStats ls = perLadder.get(mode);
        return ls != null ? ls.totalRating : 800;
    }

    /** Leaderboard position for the given display mode (-1 if unavailable). */
    public int getDisplayPosition(RankedLadder mode) {
        if (mode == RankedLadder.GLOBAL) return globalPosition;
        LadderStats ls = perLadder.get(mode);
        return ls != null ? ls.position : -1;
    }

    /** Tier derived from the ELO for the given display mode. */
    public TierInfo getDisplayTier(RankedLadder mode) {
        return TierInfo.fromElo(getDisplayElo(mode));
    }

    /** Wins for the given display mode (0 for GLOBAL). */
    public int getDisplayWins(RankedLadder mode) {
        if (mode == RankedLadder.GLOBAL) return 0;
        LadderStats ls = perLadder.get(mode);
        return ls != null ? ls.wins : 0;
    }

    /** Losses for the given display mode (0 for GLOBAL). */
    public int getDisplayLosses(RankedLadder mode) {
        if (mode == RankedLadder.GLOBAL) return 0;
        LadderStats ls = perLadder.get(mode);
        return ls != null ? ls.losses : 0;
    }

    /** Current streak for the given display mode (0 for GLOBAL). Negative = losing streak. */
    public int getDisplayStreak(RankedLadder mode) {
        if (mode == RankedLadder.GLOBAL) return 0;
        LadderStats ls = perLadder.get(mode);
        return ls != null ? ls.currentStreak : 0;
    }

    /** How many placement matches have been played for this mode. */
    public int getPlacementsPlayed(RankedLadder mode) {
        if (mode == RankedLadder.GLOBAL) return 0;
        LadderStats ls = perLadder.get(mode);
        return ls != null ? ls.placementMatchesPlayed : 0;
    }

    // -------------------------------------------------------------------------

    public static final class LadderStats {
        public final int totalRating;
        public final int wins;
        public final int losses;
        public final int currentStreak;
        public final int placementMatchesPlayed;
        public final int position;
        public final TierInfo tier;

        public LadderStats(int totalRating, int wins, int losses,
                           int currentStreak, int placementMatchesPlayed, int position) {
            this.totalRating = totalRating;
            this.wins = wins;
            this.losses = losses;
            this.currentStreak = currentStreak;
            this.placementMatchesPlayed = placementMatchesPlayed;
            this.position = position;
            this.tier = TierInfo.fromElo(totalRating);
        }

        /** True if the player has never played this ladder. */
        public boolean isUnranked() {
            return wins == 0 && losses == 0 && placementMatchesPlayed == 0;
        }

        /** True if the player is still in placement matches (has games but no ranking yet). */
        public boolean isInPlacements() {
            return !isUnranked() && position == -1;
        }

        /** Win rate as a percentage (0–100). Returns 0 if no games played. */
        public double winRate() {
            int total = wins + losses;
            return total == 0 ? 0.0 : (double) wins / total * 100.0;
        }

        public int totalGames() {
            return wins + losses;
        }
    }
}

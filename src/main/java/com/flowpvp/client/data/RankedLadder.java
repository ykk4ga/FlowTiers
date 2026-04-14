package com.flowpvp.client.data;

public enum RankedLadder {
    GLOBAL("Global"),
    HIGHEST_TIER("Highest"),
    SWORD("Sword"),
    AXE("Axe"),
    UHC("UHC"),
    VANILLA("Vanilla"),
    MACE("Mace"),
    DIAMOND_POT("Pot"),
    NETHERITE_OP("NetheriteOP"),
    SMP("SMP"),
    DIAMOND_SMP("DiamondSMP");

    private final String ladderId;

    RankedLadder(String ladderId) {
        this.ladderId = ladderId;
    }
}
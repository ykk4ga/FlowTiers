package com.flowpvp.client.config;

public enum NametagEloAlignment {
    LEFT,
    RIGHT;

    public NametagEloAlignment next() {
        return this == LEFT ? RIGHT : LEFT;
    }

    public String label() {
        return this == LEFT ? "Left" : "Right";
    }
}
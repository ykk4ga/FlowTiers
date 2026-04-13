package com.flowpvp;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowPvPMod implements ModInitializer {

    public static final String MOD_ID = "flowtiers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Common (server + client) init — nothing needed here for a client-only mod
    }
}

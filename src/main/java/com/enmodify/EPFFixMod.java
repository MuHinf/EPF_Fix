package com.enmodify;

import net.fabricmc.api.ModInitializer;

public class EPFFixMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ModConfig.load();
        System.out.println("[EPF Fix] Configuration loaded.");
    }
}
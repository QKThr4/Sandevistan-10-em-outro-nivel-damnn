package com.sandevistanmod;

import com.sandevistanmod.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(SandevistanMod.MODID)
public class SandevistanMod {
    public static final String MODID = "sandevistan";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static void LOGGER_WARN(String msg) {
        LOGGER.warn(msg);
    }

    public SandevistanMod(IEventBus modBus) {
        ModRegistries.ATTACHMENTS.register(modBus);
        ModRegistries.SOUNDS.register(modBus);
        ModRegistries.EFFECTS.register(modBus);
        ModRegistries.BLOCKS.register(modBus);
        ModRegistries.POIS.register(modBus);
        ModRegistries.PROFESSIONS.register(modBus);
        ModRegistries.ITEMS.register(modBus);
        ModRegistries.TABS.register(modBus);
    }
}

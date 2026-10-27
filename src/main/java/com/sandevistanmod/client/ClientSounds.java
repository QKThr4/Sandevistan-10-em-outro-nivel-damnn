package com.sandevistanmod.client;

import com.sandevistanmod.registry.ModRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Sons de ligar/desligar do Sandevistan, tocados no cliente presos ao jogador (acompanham quem anda).
 * Sobreposicao: o som novo sempre corta o anterior daquele jogador (ligar corta o desligar e vice-versa).
 */
public final class ClientSounds {
    private ClientSounds() {}

    private static final Map<UUID, SoundInstance> CURRENT = new HashMap<>();

    public static void onToggle(UUID id, boolean active) {
        try {
            Minecraft mc = Minecraft.getInstance();
            SoundInstance old = CURRENT.remove(id);
            if (old != null) mc.getSoundManager().stop(old);
            if (mc.level == null) return;
            Player pl = mc.level.getPlayerByUUID(id);
            if (pl == null) return;
            SoundEvent ev = (active ? ModRegistries.SND_ON : ModRegistries.SND_OFF).get();
            SoundInstance inst = new EntityBoundSoundInstance(ev, SoundSource.PLAYERS, 1.0f, 1.0f, pl,
                    pl.getRandom().nextLong());
            CURRENT.put(id, inst);
            mc.getSoundManager().play(inst);
        } catch (RuntimeException ignored) {
            // som e so enfeite: nunca derruba o jogo
        }
    }

    public static void clear() {
        CURRENT.clear();
    }
}

package com.sandevistanmod.client;

import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import com.sandevistanmod.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Musica da ciberpsicose: so quem esta com ela escuta (som local, sem posicao). Comeca baixa aos 75% e sobe ate o
 * volume maximo aos 100%; depois fica estavel. Se a musica acaba, ela recomeca. O jogador escolhe na aba do Sandevistan.
 */
@EventBusSubscriber(modid = SandevistanMod.MODID, value = Dist.CLIENT)
public final class PsyMusic {
    private PsyMusic() {}

    private static final class Track extends AbstractTickableSoundInstance {
        float target = 0.05f;
        boolean wanted = true;

        Track(SoundEvent ev) {
            super(ev, SoundSource.MASTER, RandomSource.create());
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = 0.05f;
        }

        @Override
        public void tick() {
            if (!wanted) {
                stop();
                return;
            }
            this.volume = target;
        }
    }

    private static Track current;
    private static int currentChoice;
    private static long startedAt;

    private static void stopNow() {
        if (current == null) return;
        current.wanted = false;
        Minecraft.getInstance().getSoundManager().stop(current);
        current = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stopNow();
            return;
        }
        SandevistanData d = ClientData.DATA;
        int choice = ClientLayout.music;
        boolean want = choice != 0 && d.implanted && d.psychotic && d.psy >= 75f;
        if (!want) {
            stopNow();
            return;
        }
        if (current != null && currentChoice != choice) stopNow();
        float vol = 0.05f + 0.45f * Mth.clamp((d.psy - 75f) / 25f, 0f, 1f);
        long now = System.currentTimeMillis();
        // enquanto toca a musica da ciberpsicose, a musica do jogo (so deste jogador) fica parada
        mc.getMusicManager().stopPlaying();
        if (current != null) {
            current.target = vol;
            // a musica terminou: volta a tocar
            if (now - startedAt > 2500L && !mc.getSoundManager().isActive(current)) current = null;
        }
        if (current == null) {
            SoundEvent ev = (choice == 1 ? ModRegistries.PSY_MUSIC_1 : ModRegistries.PSY_MUSIC_2).get();
            current = new Track(ev);
            current.target = vol;
            currentChoice = choice;
            startedAt = now;
            mc.getSoundManager().play(current);
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        stopNow();
    }
}

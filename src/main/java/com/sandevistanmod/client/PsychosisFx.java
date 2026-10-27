package com.sandevistanmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Efeitos de tela da ciberpsicose: tela vermelha/verde, tremor, glitches, interface cibernetica e o aviso de perigo. */
public final class PsychosisFx {
    private PsychosisFx() {}

    private static final ResourceLocation WARNING =
            ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, "textures/gui/warning.png");

    private static final String[] GLITCH_TEXT = {
            "SYSTEM FAILURE", "CYBERPSYCHOSIS DETECTED", "ERR_0x4F2A", "NEURAL LINK UNSTABLE", "WARNING",
            "SYNC LOST", "0xDEADBEEF", "RIPPERDOC REQUIRED", "MIND OVERLOAD", "CORE TEMP CRITICAL"
    };

    /** Opacidade atual do apagao (tela preta), suavizada a cada quadro. */
    private static float blindAlpha;

    /** Chance de os feixes de glitch aparecerem numa janela de 200 ms: raros ate 25%, mais e mais ate 75%, sempre depois. */
    private static float beamChance(float psy) {
        if (psy < 26f) return 0.05f + 0.07f * psy / 25f;
        if (psy < 51f) return 0.12f + 0.30f * (psy - 26f) / 25f;
        if (psy < 76f) return 0.45f + 0.40f * (psy - 51f) / 25f;
        return 1f;
    }

    private static boolean sick(SandevistanData d) {
        return d.implanted && d.psychotic && d.psy > 0f;
    }

    /** Camada embaixo da hotbar: tinta vermelha e verde (vermelho persiste mais) e barras de glitch. */
    public static void renderTint(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SandevistanData d = ClientData.DATA;
        // apagao: a tela fica preta (some e volta suavemente)
        float want = 0f;
        if (sick(d) && d.blind) {
            want = 1f;
            // a partir de 100% ele as vezes enxerga (so enxerga, o corpo continua sendo controlado)
            if (d.psy >= 100f && new Random((System.currentTimeMillis() / 1800L) * 104729L + 3L).nextFloat() < 0.3f) want = 0f;
        }
        blindAlpha += (want - blindAlpha) * 0.2f;
        if (!sick(d)) {
            if (blindAlpha > 0.02f) gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), ((int) (blindAlpha * 255f) << 24));
            return;
        }
        int w = gg.guiWidth();
        int h = gg.guiHeight();
        float psy = d.psy;
        float k = Math.min(1f, psy / 100f);
        long now = System.currentTimeMillis();

        // vermelho e verde se alternando, o vermelho dura mais e, com muita ciberpsicose, fica so vermelho
        float wave = (float) Math.sin(now / 3000.0 * Math.PI * 2.0);
        float mix = Math.max(0f, Math.min(1f, 0.5f + 0.5f * wave + 0.25f + 0.75f * k));
        float redMax;
        if (psy <= 50f) redMax = 0.35f * psy / 50f;
        else if (psy <= 100f) redMax = 0.35f + 0.25f * (psy - 50f) / 50f;
        else redMax = Math.min(0.75f, 0.6f + 0.15f * (psy - 100f) / 100f);
        int ra = (int) (mix * redMax * 255f);
        int ga = psy < 15f ? 0 : (int) ((1f - mix) * 0.15f * 255f);
        if (ra > 0) gg.fill(0, 0, w, h, (ra << 24) | 0xE00010);
        if (ga > 0) gg.fill(0, 0, w, h, (ga << 24) | 0x00FF40);

        // feixes de glitch: aparecem e somem (raros no comeco), e ficam sempre na tela depois de 75%
        if (new Random((now / 200L) * 7919L + 13L).nextFloat() < beamChance(psy)) {
            Random r = new Random(now / 130L);
            int n = 1 + (int) Math.min(11f, psy / 12f);
            for (int i = 0; i < n; i++) {
                int y = r.nextInt(Math.max(1, h));
                int bh = 2 + r.nextInt(6);
                int x0 = r.nextInt(Math.max(1, w / 2));
                int bw = w / 4 + r.nextInt(Math.max(1, w / 2));
                int col = (i % 2 == 0) ? 0x50FF1030 : 0x4000FFEE;
                gg.fill(x0, y, Math.min(w, x0 + bw), y + bh, col);
            }
        }
        // linhas de varredura
        if (psy >= 51f) {
            for (int y = (int) ((now / 40L) % 6L); y < h; y += 6) {
                gg.fill(0, y, w, y + 1, 0x18000000);
            }
        }
        if (blindAlpha > 0.02f) gg.fill(0, 0, w, h, ((int) (blindAlpha * 255f) << 24));
    }

    /** Camada por cima de tudo: aviso de perigo piscando e mensagens de erro da interface cibernetica. */
    public static void renderHud(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        SandevistanData d = ClientData.DATA;
        if (!sick(d) || d.psy <= 50f) return;
        int w = gg.guiWidth();
        int h = gg.guiHeight();
        float psy = d.psy;
        long now = System.currentTimeMillis();

        // icone de glitch (so depois de 50%): pisca e muda de lugar na tela de forma aleatoria
        Random rs = new Random((now / 450L) * 31L + 7L);
        int size = psy >= 100f ? 72 : psy >= 75f ? 60 : 48;
        boolean show = rs.nextFloat() < (psy >= 100f ? 0.9f : 0.7f) && (now / 90L) % 4L != 0L;
        int ix = 6 + rs.nextInt(Math.max(1, w - size - 12));
        int iy = 6 + rs.nextInt(Math.max(1, h - size - 12));
        if (show) {
            float alpha = 0.65f + 0.35f * (float) Math.sin(now / 180.0);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gg.setColor(1f, 1f, 1f, Math.max(0.3f, Math.min(1f, alpha)));
            gg.blit(WARNING, ix, iy, size, size, 0, 0, 128, 128, 128, 128);
            gg.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }

        // mensagens de erro em posicoes aleatorias (a partir da fase 3)
        if (psy >= 51f) {
            Random r = new Random(now / 450L);
            int n = 1 + (int) Math.min(5f, (psy - 50f) / 20f);
            for (int i = 0; i < n; i++) {
                String s = GLITCH_TEXT[r.nextInt(GLITCH_TEXT.length)];
                int x = 8 + r.nextInt(Math.max(1, w - 120));
                int y = 8 + r.nextInt(Math.max(1, h - 24));
                int a = 120 + r.nextInt(120);
                int col = (r.nextBoolean() ? 0xFF2040 : 0x20FFE0) | (a << 24);
                gg.drawString(mc.font, s, x, y, col);
            }
        }
    }

    /** Tremor da camera (roll, pitch e yaw) proporcional a ciberpsicose. */
    public static float shake(float psy, double phase) {
        float amp = Math.min(2.2f, psy / 100f * 2.0f + 0.2f);
        return (float) (Math.sin(System.currentTimeMillis() / phase) * amp);
    }

    public static boolean shaking() {
        return sick(ClientData.DATA);
    }

    public static float psy() {
        return ClientData.DATA.psy;
    }
}

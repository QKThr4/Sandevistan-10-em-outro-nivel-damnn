package com.sandevistanmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sandevistanmod.SandevistanMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Efeitos da ciberpsicose NO MODELO do jogador, visiveis para todos: tremedeira (como no frio) e glitches
 * (copias deslocadas, esticadas e coloridas do corpo/cabeca, como no anime). Tudo em tempo real.
 * Os dados vem do estado enviado pelo servidor (ClientState.psy).
 */
@EventBusSubscriber(modid = SandevistanMod.MODID, value = Dist.CLIENT)
public final class PlayerGlitch {
    private PlayerGlitch() {}

    /** region: 0 cabeca, 1 corpo, 2 tudo. strength: 1 fraco, 2 medio, 3 forte. */
    private record Glitch(long start, long end, int region, int strength, long seed) {}

    private static final class St {
        final List<Glitch> glitches = new ArrayList<>();
        long nextGlitch;
        long nextTremorRoll;
        long tremorEnd;
    }

    private static final Map<UUID, St> STATES = new HashMap<>();
    private static final Random RND = new Random();
    private static PlayerModel<AbstractClientPlayer> wide;
    private static PlayerModel<AbstractClientPlayer> slim;

    private static final int[] TINTS = {0xFF2030, 0x20F0FF, 0xFFFFFF, 0xFF30D0, 0x30FF70};

    // desfaz o tremor no fim do desenho (o Pre e o Post sao da mesma chamada)
    private static float lastWobble;
    private static double lastJx;
    private static double lastJz;

    public static void clear() {
        STATES.clear();
    }

    private static PlayerModel<AbstractClientPlayer> model(boolean isSlim) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (isSlim) {
            if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }

    private static ModelPart[] headParts(PlayerModel<AbstractClientPlayer> m) {
        return new ModelPart[] {m.head, m.hat};
    }

    private static ModelPart[] bodyParts(PlayerModel<AbstractClientPlayer> m) {
        return new ModelPart[] {m.body, m.jacket, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg,
                m.rightSleeve, m.leftSleeve, m.rightPants, m.leftPants};
    }

    // ---------- tremedeira ----------
    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre e) {
        lastWobble = 0f;
        lastJx = 0;
        lastJz = 0;
        try {
            Player pl = e.getEntity();
            int psy = ClientState.psy(pl.getUUID());
            // treme de vez em quando; a frequencia sobe ate 99%; a partir de 100% nao treme mais
            if (psy <= 0 || psy >= 100) return;
            St st = STATES.computeIfAbsent(pl.getUUID(), k -> new St());
            long now = System.currentTimeMillis();
            if (now >= st.nextTremorRoll) {
                st.nextTremorRoll = now + 500L;
                float chancePerSecond = Mth.lerp((psy - 1) / 98f, 0.04f, 0.40f);
                if (RND.nextFloat() < chancePerSecond * 0.5f) st.tremorEnd = now + 500L + RND.nextInt(900);
            }
            if (now < st.tremorEnd) {
                long step = now / 35L;
                Random r = new Random(step * 7919L + pl.getId());
                lastWobble = (r.nextFloat() * 2f - 1f) * 7f;
                lastJx = (r.nextFloat() * 2f - 1f) * 0.025;
                lastJz = (r.nextFloat() * 2f - 1f) * 0.025;
                PoseStack ps = e.getPoseStack();
                ps.translate(lastJx, 0, lastJz);
                ps.mulPose(Axis.YP.rotationDegrees(lastWobble));
            }
        } catch (RuntimeException ignored) {
            lastWobble = 0f;
        }
    }

    // ---------- glitches ----------
    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post e) {
        try {
            PoseStack ps = e.getPoseStack();
            if (lastWobble != 0f) {
                ps.mulPose(Axis.YP.rotationDegrees(-lastWobble));
                ps.translate(-lastJx, 0, -lastJz);
                lastWobble = 0f;
            }
            Player pl = e.getEntity();
            if (!(pl instanceof AbstractClientPlayer ap) || ap.isSpectator() || ap.isInvisible()) return;
            int psy = ClientState.psy(pl.getUUID());
            if (psy <= 0) return;
            Pose pose = ap.getPose();
            if (pose != Pose.STANDING && pose != Pose.CROUCHING) return;
            long now = System.currentTimeMillis();
            St st = STATES.computeIfAbsent(pl.getUUID(), k -> new St());
            spawn(st, psy, now);
            st.glitches.removeIf(g -> now > g.end());
            if (st.glitches.isEmpty()) return;
            draw(e, ap, st, now);
        } catch (RuntimeException ignored) {
            // efeito visual: nunca derruba o jogo
        }
    }

    /** Frequencia de glitches por segundo, por faixa de ciberpsicose. */
    private static float rate(int psy) {
        if (psy <= 25) return 0.18f;
        if (psy <= 50) return 0.30f;
        if (psy <= 75) return 0.45f;
        if (psy < 100) return Mth.lerp((psy - 76) / 23f, 0.7f, 1.5f);
        return 2.6f;
    }

    private static void spawn(St st, int psy, long now) {
        if (now < st.nextGlitch) return;
        float perSec = rate(psy);
        st.nextGlitch = now + (long) (1000f / perSec * (0.5f + RND.nextFloat()));
        int count = 1;
        if (psy >= 100) count = 2 + RND.nextInt(3);
        else if (psy >= 90) count = 1 + RND.nextInt(2);
        for (int i = 0; i < count; i++) {
            int region;
            int strength;
            if (psy <= 25) {
                region = 0;
                strength = 1;
            } else if (psy <= 50) {
                region = RND.nextFloat() < 0.25f ? 1 : 0;
                strength = 1;
            } else if (psy <= 75) {
                region = RND.nextFloat() < 0.35f ? 1 : 0;
                strength = RND.nextBoolean() ? 1 : 2;
            } else if (psy < 100) {
                region = RND.nextFloat() < 0.5f ? 1 : 0;
                float strong = Mth.lerp((psy - 76) / 23f, 0.3f, 0.7f);
                strength = RND.nextFloat() < strong ? 3 : (RND.nextBoolean() ? 1 : 2);
            } else {
                region = RND.nextInt(3);
                strength = 1 + RND.nextInt(3);
            }
            long dur = psy >= 100 ? 70L + RND.nextInt(200) : 90L + RND.nextInt(260);
            st.glitches.add(new Glitch(now, now + dur, region, strength, RND.nextLong()));
        }
        while (st.glitches.size() > 8) st.glitches.remove(0);
    }

    private static void draw(RenderPlayerEvent.Post e, AbstractClientPlayer ap, St st, long now) {
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        boolean isSlim = ap.getSkin().model() == PlayerSkin.Model.SLIM;
        PlayerModel<AbstractClientPlayer> m = model(isSlim);
        float bodyYaw = Mth.rotLerp(pt, ap.yBodyRotO, ap.yBodyRot);
        float headYaw = Mth.rotLerp(pt, ap.yHeadRotO, ap.yHeadRot);
        float pitch = Mth.lerp(pt, ap.xRotO, ap.getXRot());
        float limbPos = ap.walkAnimation.position(pt);
        float limbSpeed = Math.min(1.0f, ap.walkAnimation.speed(pt));
        m.young = false;
        m.crouching = ap.isCrouching();
        m.setupAnim(ap, limbPos, limbSpeed, ap.tickCount + pt, Mth.wrapDegrees(headYaw - bodyYaw), pitch);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(ap.getSkin().texture()));
        ModelPart[] head = headParts(m);
        ModelPart[] body = bodyParts(m);

        for (Glitch g : st.glitches) {
            // o glitch "pisca": muda a cada ~50 ms enquanto dura
            Random r = new Random(g.seed() + (now - g.start()) / 50L);
            int copies = g.region() == 0 ? 1 + g.strength() : 1 + (g.strength() > 1 ? 1 : 0);
            for (int c = 0; c < copies; c++) {
                float amp = g.strength() == 1 ? 0.14f : g.strength() == 2 ? 0.35f : 0.75f;
                double ox = (r.nextFloat() * 2f - 1f) * amp;
                double oy = (r.nextFloat() * 2f - 1f) * amp * 0.25f;
                double oz = (r.nextFloat() * 2f - 1f) * amp * 0.5f;
                float sx = 1f;
                float sy = 1f;
                if (g.strength() >= 2 && r.nextFloat() < 0.6f) sy = 1f + r.nextFloat() * (g.strength() == 3 ? 0.9f : 0.35f);
                if (g.strength() == 3 && r.nextFloat() < 0.4f) sx = 1f + r.nextFloat() * 0.6f;
                int tint = TINTS[r.nextInt(TINTS.length)];
                int alpha = g.strength() == 1 ? 0x70 : g.strength() == 2 ? 0x90 : 0xB0;
                int color = (alpha << 24) | tint;

                setVisible(head, g.region() != 1);
                setVisible(body, g.region() != 0);

                ps.pushPose();
                ps.translate(ox, oy, oz);
                ps.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                ps.scale(-1.0f, -1.0f, 1.0f);
                ps.scale(0.9375f * sx, 0.9375f * sy, 0.9375f * sx);
                ps.translate(0.0f, -1.501f, 0.0f);
                m.renderToBuffer(ps, vc, e.getPackedLight(), OverlayTexture.NO_OVERLAY, color);
                ps.popPose();
            }
        }
        setVisible(head, true);
        setVisible(body, true);
    }

    private static void setVisible(ModelPart[] parts, boolean v) {
        for (ModelPart p : parts) p.visible = v;
    }
}

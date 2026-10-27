package com.sandevistanmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sandevistanmod.SandevistanMod;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Afterimages do Sandevistan: silhuetas translucidas do jogador que ficam paradas no lugar por onde ele passou
 * e vao sumindo. Funciona para qualquer jogador com o Sandevistan ligado, em tempo real (nao depende dos ticks).
 */
@EventBusSubscriber(modid = SandevistanMod.MODID, value = Dist.CLIENT)
public final class Afterimages {
    private Afterimages() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, "textures/entity/afterimage.png");
    /** Quanto tempo (ms) cada silhueta dura e de quanto em quanto tempo nasce uma nova. */
    private static final long LIFE_MS = 5000L;
    private static final long SPAWN_MS = 90L;
    /** Ao desligar: cada silhueta vira branca e some em sequencia, na ordem do caminho (rastro rapido). */
    private static final long SWEEP_STEP_MS = 14L;
    private static final long SWEEP_MAX_MS = 450L;
    private static final long SWEEP_FADE_MS = 70L;
    /** Distancia minima entre silhuetas (blocos), para nao empilhar quando parado. */
    private static final double MIN_DIST_SQ = 0.12 * 0.12;
    private static final int MAX_GHOSTS = 500;

    /** 'parts' guarda a pose congelada de cada parte do corpo (x, y, z, rotX, rotY, rotZ) no instante do snapshot. */
    private record Ghost(double x, double y, double z, float bodyYaw, float[][] parts, boolean slim, long born) {}

    /** offAt: instante em que o Sandevistan desligou (0 = ainda ligado). */
    private static final class Trail {
        final ArrayDeque<Ghost> q = new ArrayDeque<>();
        long offAt;
    }

    private static final Map<UUID, Trail> GHOSTS = new HashMap<>();
    private static PlayerModel<AbstractClientPlayer> wide;
    private static PlayerModel<AbstractClientPlayer> slim;

    private static PlayerModel<AbstractClientPlayer> model(boolean isSlim) {
        Minecraft mc = Minecraft.getInstance();
        if (isSlim) {
            if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }

    private static ModelPart[] partsOf(PlayerModel<AbstractClientPlayer> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg,
                m.rightSleeve, m.leftSleeve, m.rightPants, m.leftPants};
    }

    public static void clear() {
        GHOSTS.clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = System.currentTimeMillis();
        Vec3 cam = e.getCamera().getPosition();

        // 1) novas silhuetas de quem esta com o Sandevistan ligado
        for (UUID id : ClientState.activeIds()) {
            Player pl = level.getPlayerByUUID(id);
            if (pl instanceof AbstractClientPlayer ap && ap.isAlive() && !ap.isSpectator()) {
                snapshot(ap, pt, now);
            }
        }

        // 2) desenha e descarta as velhas
        if (GHOSTS.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(TEX);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack ps = e.getPoseStack();
        boolean drew = false;

        Iterator<Map.Entry<UUID, Trail>> it = GHOSTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Trail> en = it.next();
            Player pl = level.getPlayerByUUID(en.getKey());
            Trail tr = en.getValue();
            ArrayDeque<Ghost> q = tr.q;
            // ligado: ficam todas paradas; desligou: varre o rastro em branco e some tudo
            boolean on = ClientState.active(en.getKey());
            if (on) tr.offAt = 0L;
            else if (tr.offAt == 0L) tr.offAt = now;
            int total = q.size();
            long step = total == 0 ? SWEEP_STEP_MS : Math.min(SWEEP_STEP_MS, SWEEP_MAX_MS / Math.max(1, total));
            if (!on) {
                long elapsed = now - tr.offAt;
                int gone = (int) Math.min(total, elapsed / Math.max(1L, step));
                for (int i = 0; i < gone; i++) q.pollFirst();
            }
            if (q.isEmpty()) {
                it.remove();
                continue;
            }
            if (!(pl instanceof AbstractClientPlayer ap)) {
                if (pl == null) it.remove();
                continue;
            }
            int index = 0;
            for (Ghost g : q) {
                index++;
                double dx = g.x() - cam.x;
                double dy = g.y() - cam.y;
                double dz = g.z() - cam.z;
                // nao desenha silhuetas coladas na camera (tapariam a visao)
                if (dx * dx + dz * dz < 1.0 && Math.abs(dy) < 2.0) continue;
                int color;
                if (on) {
                    // ligado: colorida e sem desvanecer
                    color = (0x80 << 24) | rainbow(g.born(), now);
                } else {
                    // desligado: feixe branco que some bem rapido, do comeco do caminho ao fim
                    long left = (now - tr.offAt) - (long) (total - q.size() + index) * step;
                    float fade = Mth.clamp(1f + left / (float) SWEEP_FADE_MS, 0f, 1f);
                    color = ((int) (fade * 0xB0) << 24) | 0xFFFFFF;
                }

                // pose congelada do instante em que a silhueta nasceu: nao depende mais do jogador
                PlayerModel<AbstractClientPlayer> m = model(g.slim());
                ModelPart[] mp = partsOf(m);
                for (int i = 0; i < mp.length; i++) {
                    float[] v = g.parts()[i];
                    mp[i].x = v[0];
                    mp[i].y = v[1];
                    mp[i].z = v[2];
                    mp[i].xRot = v[3];
                    mp[i].yRot = v[4];
                    mp[i].zRot = v[5];
                }

                ps.pushPose();
                ps.translate(dx, dy, dz);
                ps.mulPose(Axis.YP.rotationDegrees(180.0f - g.bodyYaw()));
                ps.scale(-1.0f, -1.0f, 1.0f);
                ps.scale(0.9375f, 0.9375f, 0.9375f);
                ps.translate(0.0f, -1.501f, 0.0f);
                m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
                ps.popPose();
                drew = true;
            }
        }
        if (drew) buffers.endBatch(type);
    }

    /** Cores da silhueta: verde, azul e roxo, com mistura suave entre elas. */
    private static final int[] STOPS = {0x30FF70, 0x2F8CFF, 0xA23CFF};
    /** Tempo (ms) de um ciclo completo verde -> azul -> roxo -> verde. */
    private static final float CYCLE_MS = 4500f;

    /** A cor depende de quando a silhueta nasceu (formando um rastro degrade) e muda aos poucos enquanto ela dura. */
    private static int rainbow(long born, long now) {
        float t = (born % (long) CYCLE_MS) / CYCLE_MS + (now - born) / (LIFE_MS * 2.5f);
        t = t - (float) Math.floor(t);
        float scaled = t * STOPS.length;
        int i = (int) scaled;
        float f = scaled - i;
        f = f * f * (3f - 2f * f);
        int a = STOPS[i % STOPS.length];
        int b = STOPS[(i + 1) % STOPS.length];
        int r = (int) Mth.lerp(f, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(f, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(f, a & 0xFF, b & 0xFF);
        return (r << 16) | (gr << 8) | bl;
    }

    private static void snapshot(AbstractClientPlayer ap, float pt, long now) {
        Pose pose = ap.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return;
        Trail tr = GHOSTS.computeIfAbsent(ap.getUUID(), k -> new Trail());
        tr.offAt = 0L;
        ArrayDeque<Ghost> q = tr.q;
        Vec3 pos = ap.getPosition(pt);
        Ghost last = q.peekLast();
        if (last != null) {
            if (now - last.born() < SPAWN_MS) return;
            double dx = pos.x - last.x();
            double dy = pos.y - last.y();
            double dz = pos.z - last.z();
            if (dx * dx + dy * dy + dz * dz < MIN_DIST_SQ) return;
        }
        float bodyYaw = Mth.rotLerp(pt, ap.yBodyRotO, ap.yBodyRot);
        float headYaw = Mth.rotLerp(pt, ap.yHeadRotO, ap.yHeadRot);
        float pitch = Mth.lerp(pt, ap.xRotO, ap.getXRot());
        float limbPos = ap.walkAnimation.position(pt);
        float limbSpeed = Math.min(1.0f, ap.walkAnimation.speed(pt));
        boolean isSlim = ap.getSkin().model() == PlayerSkin.Model.SLIM;
        // monta a pose AGORA com o jogador e guarda cada parte do corpo; depois a silhueta nunca mais se mexe
        PlayerModel<AbstractClientPlayer> m = model(isSlim);
        m.young = false;
        m.crouching = ap.isCrouching();
        m.setupAnim(ap, limbPos, limbSpeed, ap.tickCount + pt, Mth.wrapDegrees(headYaw - bodyYaw), pitch);
        ModelPart[] mp = partsOf(m);
        float[][] frozen = new float[mp.length][];
        for (int i = 0; i < mp.length; i++) {
            frozen[i] = new float[] {mp[i].x, mp[i].y, mp[i].z, mp[i].xRot, mp[i].yRot, mp[i].zRot};
        }
        q.addLast(new Ghost(pos.x, pos.y, pos.z, bodyYaw, frozen, isSlim, now));
        while (q.size() > MAX_GHOSTS) q.pollFirst();
    }
}

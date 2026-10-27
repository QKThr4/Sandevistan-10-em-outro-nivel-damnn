package com.sandevistanmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Alucinacoes da ciberpsicose: vultos escuros de jogadores que aparecem ao redor, so no cliente de quem esta com
 * ela, e somem uns 3 segundos depois de o jogador olhar para eles. Raros de 1% a 25%, comuns a partir de 50%.
 */
@EventBusSubscriber(modid = SandevistanMod.MODID, value = Dist.CLIENT)
public final class Hallucinations {
    private Hallucinations() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, "textures/entity/afterimage.png");
    private static final long SEEN_MS = 3000L;
    private static final long FADE_MS = 500L;
    private static final long LIFE_MS = 25000L;

    private static final class Spook {
        double x;
        double y;
        double z;
        float yaw;
        boolean slim;
        long born;
        long seenAt = -1L;
    }

    private static final List<Spook> LIST = new ArrayList<>();
    private static final Random R = new Random();
    private static long lastTick;
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

    /** Vultos por segundo: raros ate 25%, mais comuns a partir de 50% e sempre crescendo. */
    private static float rate(float psy) {
        if (psy < 26f) return 0.02f + 0.0008f * psy;
        if (psy < 50f) return 0.04f + 0.04f * (psy - 26f) / 24f;
        return Math.min(1.2f, 0.15f + (psy - 50f) * 0.004f);
    }

    public static void clear() {
        LIST.clear();
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        long now = System.currentTimeMillis();
        float dt = Mth.clamp((now - lastTick) / 1000f, 0f, 1f);
        lastTick = now;
        SandevistanData d = ClientData.DATA;
        if (p == null || level == null || !(d.implanted && d.psychotic && d.psy > 0f)) {
            LIST.clear();
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Iterator<Spook> it = LIST.iterator();
        while (it.hasNext()) {
            Spook s = it.next();
            Vec3 to = new Vec3(s.x - eye.x, s.y + 0.9 - eye.y, s.z - eye.z).normalize();
            if (s.seenAt < 0L && look.dot(to) > 0.88) s.seenAt = now;
            if ((s.seenAt >= 0L && now - s.seenAt > SEEN_MS) || now - s.born > LIFE_MS) it.remove();
        }
        int max = d.psy < 50f ? 1 : Math.min(7, 3 + (int) ((d.psy - 50f) / 25f));
        if (LIST.size() >= max || R.nextFloat() >= rate(d.psy) * 1.4f * dt) return;
        // nasce na frente do jogador (para ele poder ver), a 7-17 blocos
        float ang = (p.getYRot() + (R.nextFloat() * 2f - 1f) * 75f) * Mth.DEG_TO_RAD;
        double dist = 7.0 + R.nextDouble() * 10.0;
        double x = p.getX() - Mth.sin(ang) * dist;
        double z = p.getZ() + Mth.cos(ang) * dist;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (Math.abs(y - p.getY()) > 6.0) return;
        Spook s = new Spook();
        s.x = x;
        s.y = y;
        s.z = z;
        s.slim = R.nextBoolean();
        s.born = now;
        s.yaw = (float) (Mth.atan2(-(p.getX() - x), p.getZ() - z) * 57.2957795);
        LIST.add(s);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(TEX);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack ps = e.getPoseStack();
        for (Spook s : LIST) {
            float a = Mth.clamp((now - s.born) / 600f, 0f, 1f);
            if (s.seenAt >= 0L) {
                long t = now - s.seenAt;
                if (t > SEEN_MS - FADE_MS) a *= Mth.clamp((SEEN_MS - t) / (float) FADE_MS, 0f, 1f);
            }
            if (a <= 0.01f) continue;
            int color = ((int) (a * 0xB0) << 24) | 0x0A0A10;
            PlayerModel<AbstractClientPlayer> m = model(s.slim);
            m.young = false;
            for (ModelPart part : new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightLeg,
                    m.leftLeg, m.rightSleeve, m.leftSleeve, m.rightPants, m.leftPants}) {
                part.resetPose();
            }
            ps.pushPose();
            ps.translate(s.x - cam.x, s.y - cam.y, s.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0f - s.yaw));
            ps.scale(-1.0f, -1.0f, 1.0f);
            ps.scale(0.9375f, 0.9375f, 0.9375f);
            ps.translate(0.0f, -1.501f, 0.0f);
            m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            ps.popPose();
        }
        buffers.endBatch(type);
    }
}

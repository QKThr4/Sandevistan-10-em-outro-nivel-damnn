package com.sandevistanmod.client;

import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Ciberpsicose acima de 50%: o corpo age sozinho. A visao vira para o lado, o jogador anda ou pula para direcoes
 * aleatorias e, no apagao (tela preta), caminha ate a entidade mais proxima (jogador ou mob) e bate nela.
 * Tudo cresce com a ciberpsicose.
 */
@EventBusSubscriber(modid = SandevistanMod.MODID, value = Dist.CLIENT)
public final class ClientPsyControl {
    private ClientPsyControl() {}

    private static final Random R = new Random();
    private static int walkT;
    private static float walkF;
    private static float walkL;
    private static boolean walkJump;
    private static int driftT;
    private static float driftV;
    private static int hitCd;
    private static int retarget;
    private static LivingEntity target;

    private static boolean sick(SandevistanData d) {
        return d.implanted && d.psychotic && d.psy > 50f;
    }

    private static void reset() {
        walkT = 0;
        driftT = 0;
        hitCd = 0;
        retarget = 0;
        target = null;
    }

    private static LivingEntity nearest(Minecraft mc, LocalPlayer p) {
        LivingEntity best = null;
        double bd = 24.0 * 24.0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator()) continue;
            double dd = le.distanceToSqr(p);
            if (dd < bd) {
                bd = dd;
                best = le;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        SandevistanData d = ClientData.DATA;
        if (!sick(d)) {
            reset();
            return;
        }
        float k = Mth.clamp((d.psy - 50f) / 100f, 0f, 2f);

        if (d.blind) {
            if (target == null || !target.isAlive() || retarget-- <= 0) {
                target = nearest(mc, p);
                retarget = 10;
            }
            if (target != null) {
                double dx = target.getX() - p.getX();
                double dz = target.getZ() - p.getZ();
                double dy = target.getEyeY() - p.getEyeY();
                float wantYaw = (float) (Mth.atan2(dz, dx) * 57.2957795 - 90.0);
                float wantPitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.2957795));
                p.setYRot(p.getYRot() + Mth.clamp(Mth.wrapDegrees(wantYaw - p.getYRot()), -20f, 20f));
                p.setXRot(p.getXRot() + Mth.clamp(wantPitch - p.getXRot(), -12f, 12f));
                p.setYHeadRot(p.getYRot());
                if (p.distanceTo(target) < 3.2f && --hitCd <= 0 && mc.gameMode != null) {
                    mc.gameMode.attack(p, target);
                    p.swing(InteractionHand.MAIN_HAND);
                    hitCd = 8 + R.nextInt(6);
                }
            } else if (R.nextFloat() < 0.1f) {
                p.setYRot(p.getYRot() + (R.nextFloat() * 2f - 1f) * 40f);
            }
            return;
        }
        target = null;

        // a visao vira para o lado aos poucos
        if (driftT > 0) {
            driftT--;
            p.setYRot(p.getYRot() + driftV);
        } else if (R.nextFloat() < 0.012f + 0.01f * k) {
            driftT = 8 + R.nextInt(14);
            driftV = (R.nextBoolean() ? 1f : -1f) * (0.6f + 1.2f * k + R.nextFloat());
        }
        // anda ou pula para uma direcao aleatoria por um instante
        if (walkT > 0) {
            walkT--;
        } else if (R.nextFloat() < 0.006f + 0.006f * k) {
            walkT = 10 + R.nextInt(25);
            walkF = R.nextInt(3) - 1;
            walkL = R.nextInt(3) - 1;
            if (walkF == 0f && walkL == 0f) walkF = 1f;
            walkJump = R.nextFloat() < 0.3f + 0.2f * k;
        }
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        SandevistanData d = ClientData.DATA;
        if (p == null || e.getEntity() != p || !sick(d)) return;
        if (d.blind) {
            float f = target != null ? (p.distanceTo(target) > 2.0f ? 1f : 0f) : 0.6f;
            e.getInput().forwardImpulse = f;
            e.getInput().leftImpulse = 0f;
            e.getInput().jumping = p.horizontalCollision && p.onGround();
        } else if (walkT > 0) {
            e.getInput().forwardImpulse = walkF;
            e.getInput().leftImpulse = walkL;
            if (walkJump && p.onGround()) e.getInput().jumping = true;
        }
    }
}

package com.sandevistanmod.logic;

import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import com.sandevistanmod.data.Tx;
import com.sandevistanmod.net.Net;
import com.sandevistanmod.net.StatePayload;
import com.sandevistanmod.registry.ModRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Toda a logica do Sandevistan no servidor: implante, Mente, tick rate, atributos e efeitos. */
public final class SandevistanLogic {
    private SandevistanLogic() {}

    /** Dano instantaneo ao implantar (6 = 3 coracoes) e vida minima exigida para nao morrer. */
    public static final float IMPLANT_DAMAGE = 6f;
    public static final int IMPLANT_HEARTS = 3;
    /** Tempo, em segundos reais, para confirmar o implante com o segundo clique. */
    public static final long CONFIRM_MS = 10_000L;

    // ---- potencia do Sandevistan (tudo ajustavel aqui) ----
    /**
     * Efeito de Velocidade (o efeito de verdade, aparece no inventario): nivel = valor + 1, cada nivel soma 20%.
     * 34 -> nivel 35 -> movimento 8x (o jogo roda a 3 ticks, entao precisa ser grande, mas controlavel).
     */
    private static final int SPEED_AMP = 34;
    /** Pulo: soma na forca do pulo (padrao 0,42). 1,28 -> pulo de uns 3 blocos (com a gravidade reduzida). */
    private static final double JUMP_ADD = 1.28;
    /** Gravidade: soma (padrao 0,08, o maximo do jogo e 1,0). */
    private static final double GRAVITY_ADD = 0.5;
    /** Velocidade de ataque: soma (padrao 4) para bater a cada clique mesmo com o jogo lento. */
    private static final double ATTACK_SPEED_ADD = 100.0;
    /** Velocidade de quebrar blocos: multiplicador final. */
    private static final double BREAK_MULT = 8.0;
    /** Forca (Strength) e Pressa (Haste): nivel = valor + 1. */
    private static final int STRENGTH_AMP = 3;
    /** Resistencia IV (nivel = valor + 1) enquanto esta ligado. */
    private static final int RESISTANCE_AMP = 3;
    private static final int HASTE_AMP = 2;

    private record Boost(Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {}

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, path);
    }

    private static final Boost[] BOOSTS = {
            new Boost(Attributes.JUMP_STRENGTH, rl("jump"), JUMP_ADD, AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.GRAVITY, rl("gravity"), GRAVITY_ADD, AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.ATTACK_SPEED, rl("attack_speed"), ATTACK_SPEED_ADD,
                    AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.BLOCK_BREAK_SPEED, rl("break_speed"), BREAK_MULT - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            // sem dano de queda enquanto esta ligado (os pulos e a gravidade sao absurdos)
            // rapido tambem na agua, debaixo dagua, agachado e em blocos que atrasam (alma de areia, mel)
            new Boost(Attributes.WATER_MOVEMENT_EFFICIENCY, rl("water_move"), 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.MOVEMENT_EFFICIENCY, rl("move_eff"), 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.SUBMERGED_MINING_SPEED, rl("sub_mine"), 0.8, AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.SNEAKING_SPEED, rl("sneak"), 0.7, AttributeModifier.Operation.ADD_VALUE),
            new Boost(Attributes.FALL_DAMAGE_MULTIPLIER, rl("no_fall"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    };

    private static final Map<UUID, Long> ARMED = new HashMap<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_CODE = new HashMap<>();
    private static final Map<UUID, Float> LAST_SENT = new HashMap<>();
    /** Tick rate aplicado por nos (20 = normal). */
    private static float appliedRate = 20f;

    // ---------- utilidades ----------
    private static void chat(ServerPlayer p, Component c) {
        p.sendSystemMessage(c);
        // avisos em vermelho no chat tocam o som de alerta (so para o proprio jogador)
        net.minecraft.network.chat.TextColor col = c.getStyle().getColor();
        if (col != null && (col.equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED))
                || col.equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.DARK_RED)))) {
            playOnly(p, ModRegistries.ALERT.get(), 0.7f, 1.0f);
        }
    }

    /** Toca um som so para este jogador, junto dele. */
    private static void playOnly(ServerPlayer p, SoundEvent ev, float volume, float pitch) {
        // som preso ao jogador (segue o jogador enquanto ele anda), enviado so para ele
        p.connection.send(new ClientboundSoundEntityPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev),
                SoundSource.PLAYERS, p, volume, pitch, p.getRandom().nextLong()));
    }

    private static void bar(ServerPlayer p, Component c) {
        p.displayClientMessage(c, true);
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void send(ServerPlayer p, SandevistanData d) {
        LAST_SENT.put(p.getUUID(), d.mind);
        Net.sync(p);
    }

    // ---------- implante ----------
    /** Esmeraldas que o Ripperdoc cobra para implantar o Sandevistan. */
    public static final int CLINIC_EMERALDS = 8;

    private static int countEmeralds(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(net.minecraft.world.item.Items.EMERALD)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(net.minecraft.world.item.Items.EMERALD)) n += s.getCount();
        return n;
    }

    private static void takeEmeralds(ServerPlayer p, int amount) {
        int left = amount;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (s.is(net.minecraft.world.item.Items.EMERALD)) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (left <= 0) break;
            if (s.is(net.minecraft.world.item.Items.EMERALD)) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
    }

    /** Tem o item Sandevistan no inventario? */
    private static boolean hasImplantItem(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(ModRegistries.SANDEVISTAN_ITEM.get())) return true;
        for (ItemStack s : p.getInventory().offhand) if (s.is(ModRegistries.SANDEVISTAN_ITEM.get())) return true;
        return false;
    }

    private static void takeImplantItem(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModRegistries.SANDEVISTAN_ITEM.get())) {
                s.shrink(1);
                return;
            }
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (s.is(ModRegistries.SANDEVISTAN_ITEM.get())) {
                s.shrink(1);
                return;
            }
        }
    }

    /** Ha um Ripperdoc (aldeao doutor) perto do jogador? */
    public static boolean nearDoctor(ServerPlayer p) {
        return !p.level().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                p.getBoundingBox().inflate(8.0),
                v -> v.isAlive() && v.getVillagerData().getProfession() == ModRegistries.RIPPERDOC.get()).isEmpty();
    }

    /** O Ripperdoc implanta o Sandevistan: consome o item e 8 esmeraldas e causa 3 coracoes de dano. */
    public static boolean implantAtDoctor(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        if (d.implanted) {
            chat(p, Tx.t(ChatFormatting.RED, "already"));
            return false;
        }
        boolean creative = p.getAbilities().instabuild;
        if (!creative && !hasImplantItem(p)) {
            chat(p, Tx.t(ChatFormatting.RED, "clinic.noitem"));
            return false;
        }
        if (!creative && p.getHealth() <= IMPLANT_DAMAGE + 1f) {
            chat(p, Tx.t(ChatFormatting.RED, "lowhp", IMPLANT_HEARTS));
            return false;
        }
        if (!creative && countEmeralds(p) < CLINIC_EMERALDS) {
            chat(p, Tx.t(ChatFormatting.RED, "clinic.noemerald", CLINIC_EMERALDS));
            return false;
        }
        if (!creative) {
            takeEmeralds(p, CLINIC_EMERALDS);
            takeImplantItem(p);
        }
        p.hurt(p.damageSources().magic(), IMPLANT_DAMAGE);
        d.implanted = true;
        d.active = false;
        d.mind = d.maxMind();
        send(p, d);
        broadcastState(p, d);
        chat(p, Tx.t(ChatFormatting.GREEN, "implanted",
                Component.keybind("key.sandevistan.screen"), Component.keybind("key.sandevistan.toggle")));
        return true;
    }

    /** Remover o implante so e possivel com o Ripperdoc. */
    public static void removeImplant(ServerPlayer p, SandevistanData d) {
        if (!d.implanted) {
            chat(p, Tx.t(ChatFormatting.RED, "noimplant"));
            return;
        }
        if (d.active) {
            chat(p, Tx.t(ChatFormatting.RED, "remove.active"));
            return;
        }
        d.implanted = false;
        give(p, new ItemStack(ModRegistries.SANDEVISTAN_ITEM.get()));
        send(p, d);
        broadcastState(p, d);
        chat(p, Tx.t(ChatFormatting.YELLOW, "removed"));
    }

    /** Ciberpsicose a partir da qual o jogador nao controla mais o implante. */
    public static final float LOSS_OF_CONTROL = 100f;
    /** Dano de usar sem Mente: 2 coracoes a cada 8 segundos (o tempo acumulado se mantem entre usos). */
    private static final float ZERO_DMG_PERIOD = 8f;
    private static final float ZERO_DMG_AMOUNT = 4f;

    // ---------- ligar / desligar ----------
    public static void handleAction(ServerPlayer p, int action) {
        SandevistanData d = SandevistanData.get(p);
        switch (action) {
            case 0 -> toggle(p, d);
            case 1 -> chat(p, Tx.t(ChatFormatting.YELLOW, "remove.doctor"));
            default -> { }
        }
    }

    private static void toggle(ServerPlayer p, SandevistanData d) {
        if (!d.implanted) {
            bar(p, Tx.t(ChatFormatting.RED, "noimplant"));
            return;
        }
        if (d.psy >= LOSS_OF_CONTROL) {
            // acima de 100% o jogador perde o controle: o implante liga e desliga sozinho
            bar(p, Tx.t(ChatFormatting.DARK_RED, "psy.nocontrol"));
            return;
        }
        if (d.active) {
            deactivate(p, d, true);
        } else {
            activate(p, d);
        }
    }

    private static void activate(ServerPlayer p, SandevistanData d) {
        d.active = true;
        // ligar com a Mente em 0 tambem custa os 3 coracoes (o tick aplica ao ver a Mente zerada)
        if (d.mind <= 0f) d.zeroWarned = false;
        applyBoost(p);
        updateTickRate(p.server);
        broadcastState(p, d);
        send(p, d);
    }

    private static void deactivate(ServerPlayer p, SandevistanData d, boolean sound) {
        d.active = false;
        removeBoost(p);
        p.fallDistance = 0;
        // para o jogador no lugar: sem isso ele sairia disparado com a velocidade que tinha
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        updateTickRate(p.server);
        broadcastState(p, d);
        send(p, d);
    }

    // ---------- atributos e efeitos ----------
    private static void applyBoost(ServerPlayer p) {
        for (Boost b : BOOSTS) {
            AttributeInstance inst = p.getAttribute(b.attr());
            if (inst != null && !inst.hasModifier(b.id())) {
                inst.addTransientModifier(new AttributeModifier(b.id(), b.amount(), b.op()));
            }
        }
        ensure(p, MobEffects.MOVEMENT_SPEED, 100, 200, SPEED_AMP, true);
        ensure(p, MobEffects.DAMAGE_RESISTANCE, 100, 200, RESISTANCE_AMP, true);
        ensure(p, MobEffects.NIGHT_VISION, 300, 400, 0);
        ensure(p, MobEffects.DAMAGE_BOOST, 100, 200, STRENGTH_AMP);
        ensure(p, MobEffects.DIG_SPEED, 100, 200, HASTE_AMP);
    }

    private static void ensure(ServerPlayer p, Holder<net.minecraft.world.effect.MobEffect> eff, int minDur, int dur, int amp) {
        ensure(p, eff, minDur, dur, amp, false);
    }

    private static void ensure(ServerPlayer p, Holder<net.minecraft.world.effect.MobEffect> eff, int minDur, int dur,
                               int amp, boolean icon) {
        MobEffectInstance cur = p.getEffect(eff);
        if (cur == null || cur.getAmplifier() != amp || cur.getDuration() < minDur) {
            p.addEffect(new MobEffectInstance(eff, dur, amp, true, false, icon));
        }
    }

    private static void dropEffect(ServerPlayer p, Holder<net.minecraft.world.effect.MobEffect> eff) {
        MobEffectInstance cur = p.getEffect(eff);
        if (cur != null && cur.isAmbient()) p.removeEffect(eff);
    }

    private static void removeBoost(ServerPlayer p) {
        for (Boost b : BOOSTS) {
            AttributeInstance inst = p.getAttribute(b.attr());
            if (inst != null) inst.removeModifier(b.id());
        }
        dropEffect(p, MobEffects.MOVEMENT_SPEED);
        dropEffect(p, MobEffects.DAMAGE_RESISTANCE);
        dropEffect(p, MobEffects.NIGHT_VISION);
        dropEffect(p, MobEffects.DAMAGE_BOOST);
        dropEffect(p, MobEffects.DIG_SPEED);
    }

    // ---------- tick rate ----------
    /**
     * Enquanto algum jogador estiver com o Sandevistan ligado, o jogo inteiro roda a 2 ticks por segundo,
     * exatamente como "/tick rate 2". Ao desligar, volta a 20.
     */
    public static void updateTickRate(MinecraftServer server) {
        boolean any = false;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isAlive() && SandevistanData.get(sp).active) {
                any = true;
                break;
            }
        }
        float want = any ? SandevistanData.SLOW_TICK_RATE : 20f;
        if (want != appliedRate) {
            appliedRate = want;
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), "tick rate " + (int) want);
        }
    }

    // ---------- estado visivel para todos ----------
    private static void broadcastState(ServerPlayer p, SandevistanData d) {
        int code = d.stateCode();
        if (LAST_CODE.getOrDefault(p.getUUID(), -1) != code) {
            LAST_CODE.put(p.getUUID(), code);
            PacketDistributor.sendToAllPlayers(new StatePayload(p.getUUID(), code));
        }
    }

    public static void sendStatesTo(ServerPlayer joiner) {
        for (Map.Entry<UUID, Integer> en : LAST_CODE.entrySet()) {
            if (en.getValue() > 0 && !en.getKey().equals(joiner.getUUID())) {
                PacketDistributor.sendToPlayer(joiner, new StatePayload(en.getKey(), en.getValue()));
            }
        }
    }

    // ---------- tick do jogador ----------
    public static void tick(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        broadcastState(p, d);
        if (!d.implanted) {
            if (d.active) deactivate(p, d, false);
            return;
        }
        // com o jogo mais lento cada tick dura mais: divide pelo tick rate para o tempo real continuar o mesmo
        float rate = Math.max(1f, appliedRate);
        if (d.active) {
            // a Mente chega a 0 e o jogador pode continuar usando: cada 50 de Mente gasta soma 5 de ciberpsicose
            float cost = (SandevistanData.MAX_MIND / SandevistanData.USE_SECONDS) / rate;
            if (d.mind > 0f) {
                d.mind = Math.max(0f, d.mind - cost);
                d.useAcc += cost;
                while (d.useAcc >= SandevistanData.PSY_USE_STEP) {
                    d.useAcc -= SandevistanData.PSY_USE_STEP;
                    gainPsy(p, d, SandevistanData.PSY_PER_STEP);
                }
            } else {
                // Mente em 0: +2 de ciberpsicose por segundo de uso
                d.zeroT += 1f / rate;
                while (d.zeroT >= 1f) {
                    d.zeroT -= 1f;
                    gainPsy(p, d, 2f);
                }
            }
            boolean forced = d.psy >= LOSS_OF_CONTROL;
            if (d.mind <= 0f && !d.zeroWarned) {
                d.zeroWarned = true;
                if (!forced) {
                    chat(p, Tx.t(ChatFormatting.RED, "mindzero"));
                    // chegou em 0: 3 coracoes de dano, uma vez (so de novo depois de recuperar Mente e zerar outra vez)
                    realDamage(p, 6f);
                }
            }
            if (d.mind <= 0f) {
                // usando com a Mente em 0: 2 coracoes a cada 8 s de uso (o tempo acumulado nao zera ao parar)
                // sem o controle do implante (acima de 100%) nao ha esse dano
                if (!forced) {
                    d.zeroDmgT += 1f / rate;
                    while (d.zeroDmgT >= ZERO_DMG_PERIOD) {
                        d.zeroDmgT -= ZERO_DMG_PERIOD;
                        realDamage(p, ZERO_DMG_AMOUNT);
                    }
                }
            } else {
                d.zeroWarned = false;
            }
            applyBoost(p);
        } else {
            if (d.mind > 0f) d.zeroWarned = false;
            d.zeroT = 0f;
            if (d.mind < d.maxMind()) {
                d.mind = Math.min(d.maxMind(),
                        d.mind + (SandevistanData.MAX_MIND / SandevistanData.REGEN_SECONDS) / rate);
            }
        }
        psychosisTick(p, d, 1f / rate);
        forcedToggle(p, d, 1f / rate);
        Float last = LAST_SENT.get(p.getUUID());
        long sig = (d.blind ? 1L << 40 : 0L) + ((((long) Math.floor(d.psy) * 100L + (long) Math.ceil(d.goodMind)) * 2L + (d.psychotic ? 1L : 0L)) * 128L
                + (long) Math.floor(d.mindLost)) * 128L + (long) Math.floor(d.immuBonus);
        sig = sig * 4096L + (long) Math.ceil(d.immuTime);
        Long lastSig = LAST_SIG.get(p.getUUID());
        if (last == null || Math.abs(d.mind - last) >= 1f || (d.mind >= d.maxMind() && last < d.mind)
                || lastSig == null || lastSig != sig) {
            LAST_SIG.put(p.getUUID(), sig);
            send(p, d);
        }
    }

    /** Sem controle (ciberpsicose acima de 100%): liga e desliga sozinho, 5 a 10 segundos ligado. */
    private static void forcedToggle(ServerPlayer p, SandevistanData d, float dt) {
        if (d.psy < LOSS_OF_CONTROL) {
            d.forceT = 0f;
            return;
        }
        d.forceT -= dt;
        if (d.forceT > 0f) return;
        if (d.active) {
            deactivate(p, d, true);
            d.forceT = 3f + p.getRandom().nextFloat() * 5f;
        } else {
            activate(p, d);
            d.forceT = 5f + p.getRandom().nextFloat() * 5f;
        }
    }

    /** Dano que nao some com a Resistencia do proprio Sandevistan: compensa a reducao do efeito. */
    private static void realDamage(ServerPlayer p, float amount) {
        MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
        float factor = 1f;
        if (res != null) factor = Math.max(0.05f, 1f - 0.2f * (res.getAmplifier() + 1));
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().magic(), amount / factor);
    }

    // ---------- ciberpsicose ----------
    /** Fase 1 a 5: 1-25, 26-50, 51-75, 76-99, 100+. */
    public static int phase(float psy) {
        return psy < 26f ? 1 : psy < 51f ? 2 : psy < 76f ? 3 : psy < 100f ? 4 : 5;
    }

    /** Segundos para baixar 1 de ciberpsicose esperando com o Sandevistan desligado, por fase. */
    private static final float[] DECAY_SECONDS = {0f, 5f, 10f, 15f, 20f, 60f};
    /** Segundos para perder 1 de Mente durante os sintomas, por fase. */
    private static final float[] DRAIN_SECONDS = {0f, 30f, 15f, 7f, 5f, 1f};
    /** Forca, Pressa e Velocidade dos sintomas por fase (valor = nivel - 1; -1 = sem o efeito). */
    private static final int[] PSY_STRENGTH = {-1, 0, 0, 1, 1, 2};
    private static final int[] PSY_HASTE = {-1, -1, 0, 1, 1, 2};
    private static final int[] PSY_SPEED = {-1, -1, -1, 0, 0, 2};
    /** Chance por segundo de ataque involuntario (fases 3 a 5) e dano de cada golpe. */
    private static final float[] RAMPAGE_CHANCE = {0f, 0f, 0f, 0.12f, 0.2f, 0.35f};
    private static final float[] RAMPAGE_DAMAGE = {0f, 0f, 0f, 3f, 4f, 6f};
    /** Alcance (blocos) do ataque involuntario. */
    private static final double RAMPAGE_RANGE = 2.5;

    private static final SoundEvent[] STRANGE_SOUNDS = {
            SoundEvents.WARDEN_HEARTBEAT, SoundEvents.ENDERMAN_STARE, SoundEvents.ENDER_DRAGON_GROWL,
            SoundEvents.ELDER_GUARDIAN_CURSE, SoundEvents.WITHER_AMBIENT, SoundEvents.PHANTOM_AMBIENT
    };

    private static final Map<UUID, Long> LAST_SIG = new HashMap<>();

    private static void gainPsy(ServerPlayer p, SandevistanData d, float amount) {
        float old = d.psy;
        d.psy = Math.min(SandevistanData.MAX_PSY, d.psy + amount);
        if (old < 100f && d.psy >= 100f) chat(p, Tx.t(ChatFormatting.DARK_RED, "psy.limit"));
    }

    /** Comando (OP): mode 0 soma, 1 tira, 2 define a Mente permanente (0 a 100). */
    public static void adjustMind(ServerPlayer p, int mode, int value) {
        SandevistanData d = SandevistanData.get(p);
        float cur = SandevistanData.MAX_MIND - d.mindLost;
        float now = mode == 0 ? cur + value : mode == 1 ? cur - value : value;
        now = Math.max(0f, Math.min(SandevistanData.MAX_MIND, now));
        d.mindLost = SandevistanData.MAX_MIND - now;
        d.mind = Math.min(d.mind, d.maxMind());
        send(p, d);
    }

    /** Comando (OP): mode 0 soma, 1 tira, 2 define a Mente ATUAL (a que regenera), sem passar da Mente maxima. */
    public static void adjustCurrentMind(ServerPlayer p, int mode, int value) {
        SandevistanData d = SandevistanData.get(p);
        float now = mode == 0 ? d.mind + value : mode == 1 ? d.mind - value : value;
        d.mind = Math.max(0f, Math.min(d.maxMind(), now));
        if (d.mind > 0f) d.zeroWarned = false;
        send(p, d);
    }

    /** Comando de teste: define a ciberpsicose. */
    public static void setPsy(ServerPlayer p, float value) {
        SandevistanData d = SandevistanData.get(p);
        d.psy = Math.max(0f, Math.min(SandevistanData.MAX_PSY, value));
        send(p, d);
    }

    /** Cura Mente: recupera Mente perdida de vez (maxima sobe) e a Mente atual, sem passar de 100. */
    private static void healMind(SandevistanData d, float amount) {
        float fix = Math.min(amount, d.mindLost);
        d.mindLost -= fix;
        d.mind = Math.min(d.maxMind(), d.mind + amount);
    }

    /** Toma o Estabilizador Neural: cura 5 de Mente na hora e fica 1 minuto com Mente Boa (outro refaz o tempo e cura de novo). */
    public static boolean takeRemedy(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        if (!d.implanted) {
            chat(p, Tx.t(ChatFormatting.RED, "remedy.noimplant"));
            return false;
        }
        d.goodMind = SandevistanData.GOOD_MIND_SECONDS;
        d.goodT = 0f;
        d.goodHealT = 0f;
        healMind(d, 5f);
        syncGoodEffect(p, d);
        chat(p, Tx.t(ChatFormatting.GREEN, "remedy.used"));
        send(p, d);
        return true;
    }

    /** Imunossupressor: +20 de Mente por 1 minuto (acumula ate 100 no total); ao acabar tira o bonus e 5 por dose. */
    public static boolean takeImmuno(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        if (!d.implanted) {
            chat(p, Tx.t(ChatFormatting.RED, "remedy.noimplant"));
            return false;
        }
        float room = SandevistanData.MAX_MIND - d.maxMind();
        if (room < 1f) {
            chat(p, Tx.t(ChatFormatting.RED, "immuno.full"));
            return false;
        }
        float gain = Math.min(20f, room);
        d.immuBonus += gain;
        d.immuDoses++;
        d.immuTime += 60f;
        d.mind = Math.min(d.maxMind(), d.mind + gain);
        // tira ate 40 de ciberpsicose; essa quantidade volta quando o efeito acaba
        float took = Math.min(40f, d.psy);
        d.psy -= took;
        d.immuPsy += took;
        chat(p, Tx.t(ChatFormatting.AQUA, "immuno.used", (int) gain, (int) d.immuBonus, (int) took));
        send(p, d);
        return true;
    }

    /** Fim do Imunossupressor: some o bonus e cada dose tira 5 de Mente para sempre. */
    private static void endImmuno(ServerPlayer p, SandevistanData d, boolean notify) {
        float lost = 5f * d.immuDoses;
        float psyBack = d.immuPsy;
        d.immuBonus = 0f;
        d.immuDoses = 0;
        d.immuTime = 0f;
        d.psy = Math.min(SandevistanData.MAX_PSY, d.psy + d.immuPsy);
        d.immuPsy = 0f;
        d.mindLost = Math.min(SandevistanData.MAX_MIND, d.mindLost + lost);
        d.mind = Math.min(d.mind, d.maxMind());
        if (notify) chat(p, Tx.t(ChatFormatting.RED, "immuno.end", (int) lost, (int) psyBack));
    }

    private static void syncGoodEffect(ServerPlayer p, SandevistanData d) {
        MobEffectInstance cur = p.getEffect(ModRegistries.GOOD_MIND);
        if (d.goodMind > 0f) {
            int want = (int) (d.goodMind * 20f) + 10;
            if (cur == null || Math.abs(cur.getDuration() - want) > 60) {
                p.addEffect(new MobEffectInstance(ModRegistries.GOOD_MIND, want, 0, true, true, true));
            }
        } else if (cur != null) {
            p.removeEffect(ModRegistries.GOOD_MIND);
        }
    }

    private static void dropPsyBuffs(ServerPlayer p) {
        dropEffect(p, MobEffects.DAMAGE_BOOST);
        dropEffect(p, MobEffects.DIG_SPEED);
        dropEffect(p, MobEffects.MOVEMENT_SPEED);
    }

    /** dt = segundos reais deste tick. */
    private static void psychosisTick(ServerPlayer p, SandevistanData d, float dt) {
        if (d.immuTime > 0f) {
            d.immuTime -= dt;
            if (d.immuTime <= 0f) endImmuno(p, d, true);
        }
        // Mente Boa: 1% de chance por segundo de curar 1 de Mente e -1 de ciberpsicose a cada 5 s (com o implante desligado)
        if (d.goodMind > 0f) {
            d.goodMind = Math.max(0f, d.goodMind - dt);
            // a cada segundo com o efeito ha 1% de chance de curar 1 de Mente
            d.goodHealT += dt;
            while (d.goodHealT >= 1f) {
                d.goodHealT -= 1f;
                if (p.getRandom().nextFloat() < 0.01f) healMind(d, 1f);
            }
            if (!d.active && d.psy > 0f && d.psy < 100f) {
                d.goodT += dt;
                while (d.goodT >= 5f) {
                    d.goodT -= 5f;
                    d.psy = Math.max(0f, d.psy - 1f);
                }
            }
        }
        if (p.tickCount % 10 == 0) syncGoodEffect(p, d);

        // os sintomas valem usando o Sandevistan; parado, so comecam 5 s depois de desligar
        if (d.active) {
            d.idle = 0f;
            d.decayT = 0f;
        } else {
            d.idle += dt;
        }

        // a partir de 100% ela so sobe: +1 a cada 1,5 s, e esperar nao adianta (so o Imunossupressor alivia)
        if (d.psy >= 100f) {
            d.riseT += dt;
            while (d.riseT >= 1.5f) {
                d.riseT -= 1.5f;
                gainPsy(p, d, 1f);
            }
        } else {
            d.riseT = 0f;
        }

        // esperar com o implante desligado baixa a ciberpsicose
        if (!d.active && d.psy > 0f && d.psy < 100f) {
            d.decayT += dt;
            float period = DECAY_SECONDS[phase(d.psy)];
            while (d.psy > 0f && d.decayT >= period) {
                d.decayT -= period;
                d.psy = Math.max(0f, d.psy - 1f);
                period = DECAY_SECONDS[phase(d.psy)];
            }
        } else {
            d.decayT = 0f;
        }

        // os efeitos negativos so comecam 5 s depois de parar de usar
        boolean state = d.psy > 0f && (d.active || d.idle >= SandevistanData.PSY_IDLE_SECONDS);
        if (state != d.psychotic) {
            d.psychotic = state;
            if (state) {
                bar(p, Tx.t(ChatFormatting.DARK_RED, "psy.on"));
            } else {
                d.rampage = 0f;
                if (!d.active) dropPsyBuffs(p);
            }
        }
        if (!state) {
            d.blind = false;
            d.blindT = 0f;
            return;
        }

        int ph = phase(d.psy);
        // a partir de 100% o jogador nunca mais controla: fica sempre em apagao (o cliente deixa ver as vezes)
        if (d.psy >= 100f) {
            d.blind = true;
            d.blindT = 5f;
        }
        // apagao: acima de 50% o jogador fica sem enxergar por alguns segundos (o cliente o move e faz bater)
        if (d.blind) {
            d.blindT -= dt;
            if (d.blindT <= 0f || d.psy < 50f) {
                d.blind = false;
                d.blindT = 0f;
            }
        }
        // com o Sandevistan ligado os efeitos dele ja sao bem mais fortes: os da fase so valem parado
        if (!d.active && p.tickCount % 10 == 0) {
            if (PSY_STRENGTH[ph] >= 0) ensure(p, MobEffects.DAMAGE_BOOST, 60, 100, PSY_STRENGTH[ph]);
            if (PSY_HASTE[ph] >= 0) ensure(p, MobEffects.DIG_SPEED, 60, 100, PSY_HASTE[ph]);
            if (PSY_SPEED[ph] >= 0) ensure(p, MobEffects.MOVEMENT_SPEED, 60, 100, PSY_SPEED[ph]);
        }

        // perde Mente PARA SEMPRE: a Mente maxima cai 1 (100 vira 99) a cada intervalo da fase
        d.drainT += dt;
        float drain = DRAIN_SECONDS[ph];
        while (d.drainT >= drain) {
            d.drainT -= drain;
            d.mindLost = Math.min(SandevistanData.MAX_MIND, d.mindLost + 1f);
            d.mind = Math.min(d.mind, d.maxMind());
        }

        // ataque involuntario (1 s de golpes em quem estiver perto)
        if (d.rampage > 0f) {
            d.rampage -= dt;
            d.hitT += dt;
            while (d.hitT >= 0.25f) {
                d.hitT -= 0.25f;
                rampageHit(p, ph);
            }
        }

        // uma vez por segundo real: nausea, sons estranhos, ataque involuntario e dano
        d.secT += dt;
        while (d.secT >= 1f) {
            d.secT -= 1f;
            perSecond(p, d, ph);
        }
    }

    /** Chance por segundo do apagao: bem pequena em 50%, mais facil depois de 75%, comum em 80% e bem comum em 90%. */
    private static float blackoutChance(float psy) {
        if (psy < 75f) return 0.004f + (psy - 50f) / 25f * 0.016f;
        if (psy < 80f) return 0.03f;
        if (psy < 90f) return 0.06f + (psy - 80f) / 10f * 0.06f;
        return 0.20f + Math.min(9f, psy - 90f) / 9f * 0.15f;
    }

    private static void rampageHit(ServerPlayer p, int ph) {
        p.swing(InteractionHand.MAIN_HAND, true);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RAMPAGE_RANGE),
                x -> x != p && x.isAlive())) {
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), RAMPAGE_DAMAGE[ph]);
        }
    }

    private static void perSecond(ServerPlayer p, SandevistanData d, int ph) {
        float psy = d.psy;
        // nausea curta de vez em quando
        if (p.getRandom().nextFloat() < Math.min(0.6f, 0.04f + psy * 0.004f)) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, true, false, false));
        }
        // apagao (acima de 50%): chance por segundo e duracao crescem com a ciberpsicose
        if (!d.blind && psy >= 50f && p.getRandom().nextFloat() < blackoutChance(psy)) {
            d.blind = true;
            d.blindT = Math.min(30f, 10f + (psy - 50f) * 0.2f);
        }
        // sons estranhos (so depois de 25%), so para o proprio jogador
        if (psy > 25f && p.getRandom().nextFloat() < Math.min(0.5f, 0.05f + psy * 0.004f)) {
            // os sons de Minecraft continuam; os dois sons de psicose do mod entram no sorteio
            if (p.getRandom().nextFloat() < 0.4f) {
                playOnly(p, (p.getRandom().nextBoolean() ? ModRegistries.PSY_SND_1 : ModRegistries.PSY_SND_2).get(), 0.7f, 1.0f);
            } else {
                SoundEvent ev = STRANGE_SOUNDS[p.getRandom().nextInt(STRANGE_SOUNDS.length)];
                p.connection.send(new ClientboundSoundEntityPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev),
                        SoundSource.PLAYERS, p, 0.8f, 0.6f + p.getRandom().nextFloat() * 0.8f,
                        p.getRandom().nextLong()));
            }
        }
        // ataque involuntario
        if (d.rampage <= 0f && p.getRandom().nextFloat() < RAMPAGE_CHANCE[ph]) {
            d.rampage = 1f;
            d.hitT = 0.25f;
        }
        // dano por segundo: so a partir de 175% (1 coracao); de 225% em diante, 3 coracoes
        float dmg = 0f;
        if (psy >= 225f) dmg = 6f;
        else if (psy >= 175f) dmg = 2f;
        if (dmg > 0f) {
            realDamage(p, dmg);
        }
    }

    // ---------- morte, login, saida ----------
    public static void resetOnDeath(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        d.active = false;
        endImmuno(p, d, false);
        d.mind = d.maxMind();
        d.psy = 0f;
        d.immuPsy = 0f;
        d.blind = false;
        d.goodMind = 0f;
        d.useAcc = 0f;
        d.psychotic = false;
        d.rampage = 0f;
        d.zeroDmgT = 0f;
        d.forceT = 0f;
        removeBoost(p);
        ARMED.remove(p.getUUID());
    }

    public static void onRespawn(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        d.active = false;
        removeBoost(p);
        LAST_CODE.remove(p.getUUID());
        broadcastState(p, d);
        send(p, d);
    }

    public static void onLogin(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        // os atributos temporarios nao sao salvos: se saiu ligado, volta desligado
        d.active = false;
        LAST_CODE.remove(p.getUUID());
        sendStatesTo(p);
        broadcastState(p, d);
        send(p, d);
    }

    public static void onLogout(ServerPlayer p) {
        SandevistanData d = SandevistanData.get(p);
        if (d.active) {
            d.active = false;
            removeBoost(p);
        }
        updateTickRate(p.server);
        LAST_CODE.remove(p.getUUID());
        LAST_SENT.remove(p.getUUID());
        LAST_SIG.remove(p.getUUID());
        LAST_TOGGLE.remove(p.getUUID());
        ARMED.remove(p.getUUID());
        PacketDistributor.sendToAllPlayers(new StatePayload(p.getUUID(), 0));
    }

    public static void clearAll() {
        ARMED.clear();
        LAST_TOGGLE.clear();
        LAST_CODE.clear();
        LAST_SENT.clear();
        LAST_SIG.clear();
        appliedRate = 20f;
    }
}
